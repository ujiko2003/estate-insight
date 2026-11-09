# BACKLOG

> 남은 작업을 하루 단위로 쪼개고, 각 작업에 완료 조건(DoD)을 붙인다. 완료된 작업은 맨 아래
> "완료" 섹션에 별도로 모아둔다.
> 마지막 갱신: 2026-11-09 — 1단계는 하루 단위로 분해했고, 2~4단계는 1단계 완료 후 상세
> 분해할 예정이라 지금은 단계 단위의 큰 덩어리로만 남겨둔다 (`OPEN_QUESTIONS.md` 참조).

## 진행 중 — 1단계 로컬 MVP

### 1-4. 배치 틀

- [ ] **종료 코드 처리**: `BatchApplication`의 `main`을
  `System.exit(SpringApplication.exit(SpringApplication.run(...)))` 형태로 변경.
  DoD: Job을 의도적으로 실패시켰을 때 프로세스 종료 코드가 0이 아님을 `echo $?`로 확인,
  성공 시 0인 것도 함께 확인.
- [ ] **Job/Step/Tasklet 뼈대 작성**: `runDate` 파라미터를 받는 `@Configuration` 클래스,
  Tasklet은 로그 한 줄만 찍는 수준으로 단순하게.
  DoD: `./gradlew :batch:bootRun --args='runDate=2026-11-06'` 성공.
- [ ] **재실행 방지 확인**: 같은 `runDate`로 두 번째 실행.
  DoD: 두 번째 실행이 `BATCH_JOB_INSTANCE`의 `(JOB_NAME, JOB_KEY)` UNIQUE 제약으로 막히는
  것을 실제 로그/에러로 확인.

### 1-5. 실거래가 API 클라이언트

- [ ] **RestClient 호출 기본 틀**: 국토교통부 실거래가 상세 API를 `LAWD_CD` + `DEAL_YMD`로
  호출.
  DoD: 실제 서비스키로 한 건 호출해 원본 응답(XML 또는 JSON) 확인.
- [ ] **응답 파싱 + 금액 처리**: 쉼표/공백이 섞인 금액 문자열을 정수로 변환, XML/JSON 파싱
  로직 작성, 단위 테스트 추가.
  DoD: 쉼표 포함 금액, 공백 포함 금액 등 엣지 케이스 테스트 통과.

### 1-6. 수집 Step

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
