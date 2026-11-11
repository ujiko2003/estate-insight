# BACKLOG

> 남은 작업을 하루 단위로 쪼개고, 각 작업에 완료 조건(DoD)을 붙인다. 완료된 작업은 맨 아래
> "완료" 섹션에 별도로 모아둔다.
> 마지막 갱신: 2026-11-09 — 1단계는 하루 단위로 분해했고, 2~4단계는 1단계 완료 후 상세
> 분해할 예정이라 지금은 단계 단위의 큰 덩어리로만 남겨둔다 (`OPEN_QUESTIONS.md` 참조).

## 진행 중 — 1단계 로컬 MVP

### 1-6. 수집 Step — 진행 중 (브리프: `briefs/1-6.md`)

선행 작업 2건은 2026-11-11 추가(Hy가 PM 판단에 위임). 1-5에서 드러난 문제라 Chunk Step보다 먼저 한다.

- [ ] **[선행] `umd_code` 컬럼 길이 수정**: `apt_complex.umd_code`가 `CHAR(3)`인데 실제 `umdCd`는
  `10600`처럼 5자리다. V5 마이그레이션으로 `VARCHAR(5)`로 바꾼다(`sgg_code`의 7번 결정과 같은 이유).
  DoD: `api` 기동으로 V5 적용 후 `information_schema.columns`에서 `character varying(5)` 확인.
- [ ] **[선행] 테스트에서 Job 자동 실행 끄기**: `BatchApplicationTests`가 컨텍스트를 띄울 때 실제 Job이
  실행되지 않게 한다(예: 테스트 설정에 `spring.batch.job.enabled=false`).
  DoD: `./gradlew :batch:test` 전체 실행 전후 `batch_job_execution` 행 수가 같다.
- [ ] **Chunk Step 설계**: 지역×계약월 단위로 나눠 처리하는 Chunk Step.
  DoD: 더미 데이터로 Step이 끝까지 실행되는 것 확인.
- [ ] **원본 로컬 파일 저장**: 수집한 원본 응답을 로컬 파일로 저장 (2단계에서 S3로 교체 예정).
  DoD: 실행 후 파일이 실제로 생성되는지 확인.
- [ ] **upsert 로직**: `trade_key` 기준으로 저장, 이미 있는 거래는 갱신만.
  DoD: 같은 거래를 두 번 수집해도 행이 중복되지 않는 것 확인.
- [ ] **`ingest_status` 갱신**: 지역×계약월 단위 수집 상태를 `PENDING → IN_PROGRESS → DONE`
  (또는 `FAILED`)로 갱신.
  DoD: 수집 성공/실패 각각에 대해 상태 전이가 올바른지 확인.

### 1-7. 지오코딩

- [ ] **캐시 우선 조회**: `geocode_cache`를 먼저 조회하고, 없을 때만 외부 지오코딩 API 호출.
  DoD: 같은 주소를 두 번 조회할 때 외부 API가 한 번만 호출되는 것 확인.
- [ ] **실패 기록**: 지오코딩 실패(`NOT_FOUND`)도 캐시에 저장.
  DoD: 실패한 주소를 재조회해도 외부 API가 다시 호출되지 않는 것 확인.

### 1-8. 조회 API 4종

- [ ] **실거래 조건 검색**: 시군구·기간·가격대 조건.
  DoD: `GET /api/trades?sgg=...&from=...&to=...&minPrice=...&maxPrice=...` 정상 응답.
- [ ] **지도 검색**: bbox / 반경 검색, `ST_MakeEnvelope` / `ST_DWithin` 사용.
  DoD: 지도 영역·반경 각각에 대해 실제 좌표로 테스트, 범위 밖 데이터가 제외되는 것 확인.
- [ ] **단지별 가격 추이**: 월별 평균 가격.
  DoD: 특정 `aptSeq`로 월별 집계가 올바른 값으로 나오는지 확인.
- [ ] **시군구 통계**: 평균 평당가·거래량, 해제 거래(`is_cancelled`) 제외.
  DoD: 해제된 거래를 포함한 테스트 데이터로 집계 결과에서 해제 거래가 빠지는 것 확인.

### 1-9. 테스트

- [ ] **Testcontainers 설정**: PostGIS 컨테이너를 테스트에서 띄우는 설정.
  DoD: 테스트 실행 시 컨테이너가 자동으로 뜨고 내려가는 것 확인.
- [ ] **리포지토리/배치 통합 테스트**: 주요 리포지토리 쿼리와 1-6의 Chunk Step에 대한 통합
  테스트.
  DoD: `./gradlew test`(전체) 통과.

## 예정 — 2단계 이후 (상세 분해 전)

- **2단계 AWS 배포**: Terraform(VPC/RDS/ECS/S3/ECR), GitHub Actions OIDC 배포 파이프라인,
  EventBridge 스케줄 배치. AWS Budgets 알람 설정이 선행 조건.
- **3단계 확장 + 성능**: 전국·수년치 데이터 적재, EXPLAIN 기반 인덱스 튜닝, 파티셔닝 전후
  비교, Redis 캐시 적용, k6 부하 테스트 전후 수치 비교.
- **4단계 운영 + 고가용성**: CloudWatch 알람, Multi-AZ 전환, AWS FIS 장애 훈련, 포스트모템
  작성.

---

## 완료

### 1-1. 멀티모듈 Gradle 뼈대
- `settings.gradle.kts`, 루트/모듈별 `build.gradle.kts`, Gradle wrapper, 두 Application
  클래스, `.gitignore` 작성 완료.
- DoD 확인: `./gradlew build` 성공, `api`가 `batch` 의존성을 전혀 모르는 것을
  `:api:dependencies`로 확인.

### 1-2. 로컬 인프라
- `compose.yaml`(postgis/postgis:16-3.4, redis:7-alpine, healthcheck), `api/application.yml`
  (환경변수 기본값 패턴), Flyway V1~V3 작성 완료.
- DoD 확인: `docker compose up` 후 두 컨테이너 모두 healthy, Flyway V1~V3 적용, 실제 DB에
  접속해 테이블·인덱스·확장(PostGIS) 전부 검증, `/actuator/health` UP.

### 1-3. 동작 확인 API
- `Region` 엔티티·리포지토리, `RegionResponse` record, `GET /api/regions` 컨트롤러 작성 완료.
- 스키마 타입 불일치(`CHAR` vs `VARCHAR`) 해결을 위한 V4 마이그레이션 추가.
- DoD 확인: `GET /api/regions` → 25개 구 응답, 강남구(11680)만 `isActive: true`.

### 1-4 (부분). batch 모듈 DB 연결
- `batch/application.yml` 작성 완료 (Flyway 관련 설정 없이 DB 연결 정보만).
- DoD 확인: `batch:test` 통과, `./gradlew build`(테스트 포함) 전체 통과.

### 1-4 (부분). Job/Step/Tasklet 뼈대
- `JobConfig`에 `logTestJob`/`logTestStep` 작성, `runDate` 파라미터 사용 (커밋 `9bd1771`).
- DoD 확인: `BATCH_JOB_EXECUTION`에 `logTestJob`, `runDate=2026-11-06`, `COMPLETED` 기록 확인.

### 1-4 (부분). 종료 코드 처리
- `BatchApplication`의 `main`에 `System.exit(SpringApplication.exit(...))` 적용 (커밋 `9bd1771`).
- DoD 확인: 성공 시 종료 코드 0(`runDate=2026-12-01`), 의도적 실패 시 5(`runDate=2026-12-02`).
  실행 로그는 Hy 보고, Job 상태는 `BATCH_JOB_EXECUTION`에서 대조.

### 1-4 (부분). 재실행 방지 확인
- DoD(2026-11-09 문구 수정): 같은 `runDate`의 두 번째 실행이 완료된 JobInstance라서 거부되는 것을
  실제 로그/에러로 확인.
- DoD 확인: `runDate=2026-12-01` 재실행 시 `JobInstanceAlreadyCompleteException`, 종료 코드 1.
  `BATCH_JOB_EXECUTION`에 해당 `runDate` 실행은 1건만 존재.

### 1-5 (부분). RestClient 호출 기본 틀
- `AptTradeApiClient`, `AptTradeXmlParser` 작성 (커밋 `d7ca28a`, 브랜치 `feat/1-5-api-client`).
- DoD 확인: 실제 서비스키로 호출해 원본 응답 확인. `resultCode` 000, 강남구 2026년 9월 `totalCount`
  125건은 Hy 기록(커밋 `aface3c`). 실행은 `BATCH_JOB_EXECUTION`
  (`runDate=2026-12-05`, COMPLETED, Step 224ms)에서 대조. 커밋된 코드에는 호출부가 없다.

### 1-5 (부분). 응답 파싱 + 금액 처리 (브리프: `briefs/1-5.md`)
- `AptTradeFieldParser.parseDealAmount`, 실제 응답 테스트 리소스 2건(강남구 2026-09 125건,
  2026-12 0건) 추가 (커밋 `f25bc0d`, `main` 병합 `f5707b3`).
- DoD 확인: `./gradlew :batch:test --tests 'com.hy.estate.batch.*'` 17건 통과(금액 12, 파서 5)를
  PM이 재실행으로 확인. 쉼표·공백·null·빈 값·숫자 아님·0·음수, `item` 1건, 0건, `resultCode` 오류,
  접근거부 포함. 실제 응답 125건 금액 전부 변환. 테스트 리소스에 서비스키 없음.
