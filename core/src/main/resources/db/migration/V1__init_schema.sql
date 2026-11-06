-- =========================================================
-- V1__init_schema.sql
-- 공공데이터 기반 부동산 정보 서비스 - 초기 스키마 (PostgreSQL + PostGIS)
-- 위치: batch/core 공통 모듈의 src/main/resources/db/migration/
-- =========================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------
-- 1. 지역 (시군구)
--    API 요청 파라미터 LAWD_CD(5자리)와 같은 코드
-- ---------------------------------------------------------
CREATE TABLE region (
    sgg_code    CHAR(5)      PRIMARY KEY,           -- 예: 11680 (서울 강남구)
    sido_name   VARCHAR(20)  NOT NULL,              -- 서울특별시
    sgg_name    VARCHAR(30)  NOT NULL,              -- 강남구
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE  -- 수집 대상 여부 (MVP는 1개 구만 TRUE)
);

-- ---------------------------------------------------------
-- 2. 아파트 단지
--    API의 aptSeq(시군구코드-일련번호)를 그대로 PK로 사용
-- ---------------------------------------------------------
CREATE TABLE apt_complex (
    apt_seq         VARCHAR(20)  PRIMARY KEY,                     -- 예: 11680-2246
    apt_name        VARCHAR(100) NOT NULL,
    sgg_code        CHAR(5)      NOT NULL REFERENCES region (sgg_code),
    umd_code        CHAR(3),                                      -- 읍면동 코드
    umd_name        VARCHAR(30),                                  -- 법정동 이름
    jibun_address   VARCHAR(200),                                 -- 지번 주소 (bonbun/bubun 조립)
    road_address    VARCHAR(200),                                 -- 도로명 주소
    build_year      SMALLINT,
    location        GEOGRAPHY(POINT, 4326),                       -- 지오코딩 결과 (실패 시 NULL)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 지도 영역/반경 검색용 공간 인덱스
CREATE INDEX idx_apt_complex_location ON apt_complex USING GIST (location);
CREATE INDEX idx_apt_complex_sgg      ON apt_complex (sgg_code);

-- ---------------------------------------------------------
-- 3. 실거래
-- ---------------------------------------------------------
CREATE TABLE apt_trade (
    id                BIGSERIAL     PRIMARY KEY,
    trade_key         CHAR(64)      NOT NULL UNIQUE,   -- 자연키 해시 (upsert 기준, 아래 설명)
    apt_seq           VARCHAR(20)   NOT NULL REFERENCES apt_complex (apt_seq),
    sgg_code          CHAR(5)       NOT NULL,          -- 지역 통계용 비정규화
    deal_date         DATE          NOT NULL,          -- dealYear + dealMonth + dealDay
    deal_amount       INTEGER       NOT NULL,          -- 만원 단위 (API 원본 단위 유지)
    exclusive_area    NUMERIC(8, 2) NOT NULL,          -- 전용면적(㎡) excluUseAr
    floor             SMALLINT,
    apt_dong          VARCHAR(30),
    dealing_type      VARCHAR(10),                     -- 중개거래 / 직거래
    buyer_type        VARCHAR(10),                     -- 개인 / 법인 등
    seller_type       VARCHAR(10),
    registered_date   DATE,                            -- 등기일자 (나중에 채워질 수 있음)
    is_cancelled      BOOLEAN       NOT NULL DEFAULT FALSE,  -- cdealType = 'O'
    cancelled_date    DATE,                            -- cdealDay
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 단지별 가격 추이
CREATE INDEX idx_trade_apt_date ON apt_trade (apt_seq, deal_date);
-- 지역·기간 통계 (해제 거래 제외 조회가 대부분이라 부분 인덱스)
CREATE INDEX idx_trade_sgg_date ON apt_trade (sgg_code, deal_date) WHERE is_cancelled = FALSE;

-- ---------------------------------------------------------
-- 4. 지오코딩 캐시
--    같은 주소는 외부 API를 다시 호출하지 않기 위함
-- ---------------------------------------------------------
CREATE TABLE geocode_cache (
    address_key     VARCHAR(200) PRIMARY KEY,          -- 정규화한 주소 (공백·표기 통일)
    location        GEOGRAPHY(POINT, 4326),
    provider        VARCHAR(20)  NOT NULL,             -- VWORLD / KAKAO
    status          VARCHAR(20)  NOT NULL,             -- SUCCESS / NOT_FOUND / FAILED
    attempt_count   SMALLINT     NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------
-- 5. 수집 이력 (지역 x 계약년월 단위)
--    API 호출 한도로 중단돼도 어디까지 받았는지 알고 이어서 받기 위함
-- ---------------------------------------------------------
CREATE TABLE ingest_status (
    sgg_code        CHAR(5)      NOT NULL REFERENCES region (sgg_code),
    deal_ym         CHAR(6)      NOT NULL,             -- API 파라미터 DEAL_YMD (예: 202401)
    status          VARCHAR(20)  NOT NULL,             -- PENDING / IN_PROGRESS / DONE / FAILED
    total_count     INTEGER,                           -- API가 알려준 전체 건수
    saved_count     INTEGER,                           -- 실제 저장 건수
    raw_s3_key      VARCHAR(300),                      -- 원본 보관 위치
    last_error      TEXT,
    collected_at    TIMESTAMPTZ,
    PRIMARY KEY (sgg_code, deal_ym)
);
