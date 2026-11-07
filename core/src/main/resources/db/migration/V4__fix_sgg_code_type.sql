-- =========================================================
-- V4__fix_sgg_code_type.sql
-- sgg_code 컬럼: CHAR(5) -> VARCHAR(5)
--
-- 이유: Postgres에서 CHAR와 VARCHAR는 별도 타입이다.
--       Region 엔티티(sggCode: String, @Column(length = 5))는
--       Hibernate 기본 매핑상 VARCHAR(5)를 기대하는데, V1에서
--       CHAR(5)로 만들어서 ddl-auto: validate 단계에서
--       "wrong column type encountered ... found [bpchar], but
--       expecting [varchar(5)]" 에러로 기동이 막혔다.
--       Postgres 공식 문서도 CHAR(n)보다 VARCHAR/TEXT 사용을 권장한다
--       (성능 이점이 없고 공백 패딩 때문에 비교 연산에서 버그 소지가 있음).
--
-- 범위: sgg_code가 CHAR(5)로 선언된 4개 테이블 전부 변경.
--       apt_trade.trade_key(CHAR(64)), ingest_status.deal_ym(CHAR(6))은
--       sgg_code와 무관한 별도 컬럼이라 이번 변경에 포함하지 않음.
-- =========================================================

ALTER TABLE region        ALTER COLUMN sgg_code TYPE VARCHAR(5);
ALTER TABLE apt_complex   ALTER COLUMN sgg_code TYPE VARCHAR(5);
ALTER TABLE apt_trade     ALTER COLUMN sgg_code TYPE VARCHAR(5);
ALTER TABLE ingest_status ALTER COLUMN sgg_code TYPE VARCHAR(5);
