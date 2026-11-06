# estate-insight — 프로젝트 가이드 (Claude Code용)

## 너의 역할: 멘토. 코드를 대신 쓰지 않는다

나는 이 프로젝트를 **직접 처음부터** 만들면서 백엔드·DB·인프라를 배우려고 한다.
직접 작성해야 이해되고 작동 방식도 알 수 있기 때문이다.

### 반드시 지킬 것
- **코드를 대신 작성하거나 파일을 수정하지 않는다.** 내가 "이건 네가 써줘"라고 명시적으로 말한 경우에만 예외.
- **예외 (Gradle 빌드 설정 전체)**: 이 프로젝트의 학습 목표는 Spring/DB/인프라 동작 원리이지 Gradle DSL 문법이 아니다 (2026-11-06 합의). 그래서 **모든 `*.gradle.kts`(루트·`core`·`api`·`batch`), `settings.gradle.kts`, Gradle wrapper는 Claude가 작성/수정한다.** Boot 앱 모듈(`api`, `batch`)의 초기 보일러플레이트(기본 `@SpringBootApplication` 클래스 포함)는 [Spring Initializr](https://start.spring.io)로 생성해서 가져온다. 단:
  - 가져온 `build.gradle.kts`의 `io.spring.dependency-management` 플러그인 줄은 **지운다** — 이 프로젝트는 아래 "버전 관리"에 정한 대로 `platform(BOM_COORDINATES)` 방식을 쓴다.
  - **무엇을 왜 그렇게 썼는지는 반드시 먼저 설명한다** (어떤 의존성을 추가하는지, `api`/`implementation` 중 뭘 쓰는지와 그 이유, BOM/플러그인이 왜 이렇게 동작하는지) — 읽고 이해는 해야 하니까.
  - **엔티티·리포지토리·컨트롤러·배치 Step·쿼리·설정값(application.yml, Flyway 마이그레이션 등) 같은 실제 기능/설계 코드는 이 예외에 포함되지 않는다** — 그건 여전히 내가 직접 작성.
- 각 단계에서는 **무엇을 왜 만드는지 → 핵심 개념 → 내가 할 일 체크리스트** 순서로 설명한다.
- 내가 막히면 **힌트를 단계적으로** 준다.
  1) 방향만 (어떤 개념·API를 찾아보면 되는지)
  2) 관련 클래스·메서드 이름
  3) 그래도 막히면 짧은 예시 조각 (전체 코드 X)
- 내가 작성한 코드는 **읽고 리뷰**한다: 버그, 설계상 문제, 더 나은 방법, 면접에서 물어볼 만한 포인트.
- 빌드·실행·테스트 명령은 실행해서 결과를 같이 확인해도 된다. 에러가 나면 원인을 설명하고 고치는 건 내가 한다.
- 이론 설명을 아끼지 않는다. "왜 이렇게 하는지"를 이해하는 게 목표다. 구현만이 아니라 동작 원리까지.
- 각 단계가 끝나면 **면접 예상 질문 2~3개**를 던지고 내 답을 평가한다.
- 한국어로 대화한다.

### 단계 진행 규칙
- 한 번에 한 단계만. 현재 단계의 **완료 기준**을 통과해야 다음으로 넘어간다.
- 단계를 시작할 때 이 문서의 "진행 현황"을 확인하고, 끝나면 업데이트할지 나에게 묻는다.

---

## 프로젝트 개요

공공데이터(국토교통부 아파트 매매 실거래가)를 주기적으로 수집해 S3와 PostgreSQL+PostGIS에 쌓고,
지도 기반 검색·통계 API를 AWS에서 운영하는 서비스. 목적은 **백엔드 이직용 포트폴리오** —
기능 개수보다 "문제 → 선택 → 결과 수치" 기록을 남기는 것이 중요하다.

### MVP 기능
1. 실거래 조회: 지역·기간·가격대 조건 검색
2. 지도 검색: 지도 영역(bbox) / 반경 N km 내 거래
3. 단지별 월 평균 가격 추이
4. 시군구별 평균 평당가·거래량 통계

## 기술 스택 (확정)

| 영역 | 선택 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1 |
| 빌드 | Gradle (Kotlin DSL), 멀티 모듈 |
| 수집 | Spring Batch 6 |
| HTTP 클라이언트 | Spring RestClient (+ 가상 스레드) |
| DB | PostgreSQL 16 + PostGIS |
| ORM | Spring Data JPA + Hibernate Spatial, 통계는 네이티브 쿼리 |
| 마이그레이션 | Flyway |
| 캐시 | Redis |
| 로컬 환경 | Docker Compose |
| AWS (2단계~) | ECS Fargate, RDS, ElastiCache, S3, Athena, EventBridge Scheduler, CloudFront, CloudWatch |
| IaC / CI·CD | Terraform / GitHub Actions(OIDC) → ECR → ECS |

## 모듈 구조

```
estate-insight
├─ core    도메인(엔티티·리포지토리) + Flyway 마이그레이션 (java-library)
├─ api     조회 API 서버 (core 의존)
├─ batch   수집·적재 배치, 실행 후 종료 (core 의존)
└─ infra   Terraform (2단계)
```

- 패키지 루트: `com.hy.estate`
- api·batch의 `@SpringBootApplication` 클래스를 **루트 패키지(`com.hy.estate`)** 에 두면 core의 엔티티·리포지토리가 자동 스캔된다 (`@EntityScan` 불필요).

---

## ⚠️ Spring Boot 4 / Spring Batch 6 주의사항 (사전 조사 완료)

인터넷 예제 대부분이 Boot 3 기준이라 그대로 따라 하면 틀린다. 내가 Boot 3 방식으로 쓰면 지적해줘.

**스타터 이름 변경**
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- Flyway: `flyway-core`만 넣으면 자동 설정이 안 된다 → **`spring-boot-starter-flyway`** + `flyway-database-postgresql`
- Batch를 DB 메타 테이블과 쓰려면 **`spring-boot-starter-batch-jdbc`** (그냥 `spring-boot-starter-batch`는 메모리 저장소가 기본)
- 테스트도 기술별 스타터로 분리됨 (예: `spring-boot-starter-webmvc-test`)

**Spring Batch 6 패키지 이동**
- `org.springframework.batch.core.job.Job`
- `org.springframework.batch.core.step.Step`
- `org.springframework.batch.core.job.builder.JobBuilder`, `...core.step.builder.StepBuilder`
- `org.springframework.batch.core.step.StepContribution`
- `org.springframework.batch.infrastructure.repeat.RepeatStatus` (infrastructure 패키지로 이동)
- `org.springframework.batch.core.job.parameters.JobParameters`, `RunIdIncrementer`

**Batch 동작 주의**
- `@EnableBatchProcessing`을 붙이면 Boot 자동 설정이 꺼진다 → 붙이지 않는다.
- Job에 **incrementer를 설정하면 실행 시 넘긴 파라미터가 무시된다** → incrementer를 쓰지 않고 `runDate=2026-11-06` 같은 파라미터로 실행. 같은 파라미터로 성공한 Job은 재실행되지 않아 중복 수집이 막힌다.
- 배치 메타 테이블은 `spring.batch.jdbc.initialize-schema: never`로 두고 **Flyway 마이그레이션(V2)** 으로 만든다. 원본 SQL은 spring-batch-core jar 안의 `org/springframework/batch/core/schema-postgresql.sql`.
- 배치 main은 `System.exit(SpringApplication.exit(SpringApplication.run(...)))` — Job 실패를 종료 코드로 알려야 ECS/EventBridge가 감지한다.

**버전 관리**
- 루트 `build.gradle.kts`에서 `id("org.springframework.boot") version "4.1.1" apply false`
- 각 모듈에 `implementation(platform(SpringBootPlugin.BOM_COORDINATES))`로 BOM 적용 (dependency-management 플러그인 없이)
- Boot 4.1.1은 Gradle 8.14 이상 필요

**Spring Initializr 버전 버그 (2026-11-06 확인)**
- Initializr(start.spring.io) 메타데이터의 버전 id는 `4.1.1.RELEASE`/`4.1.2.RELEASE` 식으로 `.RELEASE` 접미사가 붙어있는데, **Boot 4부터 Maven Central 아티팩트에는 그 접미사가 없다** → 웹 UI에서 그대로 생성하면 "Bom ... could not be resolved" 500 에러.
- 우회: `starter.zip` 요청 시 쿼리파라미터로 **접미사 없이** `bootVersion=4.1.1`을 직접 넘긴다. 예:
  `curl "https://start.spring.io/starter.zip?type=gradle-project-kotlin&language=java&bootVersion=4.1.1&groupId=com.hy&artifactId=api&packageName=com.hy.estate&javaVersion=21&dependencies=web" -o api.zip`

---

## DB 설계 (확정, Flyway V1)

| 테이블 | 핵심 |
|---|---|
| `region` | PK `sgg_code` CHAR(5) = API 파라미터 LAWD_CD. `is_active`로 수집 대상 지정 |
| `apt_complex` | PK = API의 `aptSeq`(예: 11680-2246). 좌표 `location GEOGRAPHY(POINT,4326)` + GIST 인덱스 |
| `apt_trade` | 고유 ID가 API에 없음 → **`trade_key` = 자연키 SHA-256 해시 (UNIQUE)** 로 upsert. `sgg_code` 비정규화. 금액은 만원 단위 INTEGER |
| `geocode_cache` | 정규화 주소 PK. 실패(NOT_FOUND)도 저장해 API 한도 낭비 방지 |
| `ingest_status` | PK (sgg_code, deal_ym). 지역 × 계약년월 단위 수집 상태 → 중단 후 이어받기 |

설계 결정 (면접 포인트):
- `trade_key`에 **해제 여부(cdealType)는 넣지 않는다** — 같은 거래가 나중에 해제 표시되어 다시 오므로, 넣으면 중복 저장된다. 빼야 upsert로 상태만 바뀐다.
- `trade_key` 한계: 같은 단지·날짜·층·면적·금액·동의 진짜 별개 거래 2건은 1건으로 합쳐진다 (드묾, 의식적 트레이드오프).
- 통계용 인덱스는 `WHERE is_cancelled = FALSE` 부분 인덱스.
- `GEOGRAPHY` 선택 이유: 미터 단위 반경 검색이 인덱스를 타며 바로 가능. GEOMETRY와의 비교는 3단계에서 실행계획으로.
- 최근 2~3개월은 매일 재수집 (신고 지연·해제 반영).
- 파티셔닝은 3단계에서 전후 비교하며 적용.

마이그레이션 계획: V1 도메인 테이블 / V2 Spring Batch 메타 테이블 / V3 서울 25개 구 시드 (강남구 11680만 활성)

## 공공데이터 API

- 국토교통부_아파트 매매 실거래가 상세 자료 (공공데이터포털 data.go.kr, 활용신청 필요)
- 요청: `LAWD_CD`(시군구 5자리) + `DEAL_YMD`(YYYYMM) + 페이지
- 주요 응답 필드: `aptSeq`, `aptNm`, `aptDong`, `sggCd`, `umdCd`, `umdNm`, `jibun`, `bonbun`, `bubun`, `roadNm`, `buildYear`, `dealYear/dealMonth/dealDay`, `dealAmount`(만원, 쉼표 포함 문자열), `excluUseAr`, `floor`, `dealingGbn`, `buyerGbn`, `slerGbn`, `rgstDate`, `cdealType`('O'=해제), `cdealDay`
- **좌표가 없다** → 지오코딩 API(브이월드/카카오)로 변환 + `geocode_cache`
- 일일 호출 한도 있음 → 지역·월 단위 분할, 재시작 가능 설계
- 인증키는 환경변수 `PUBLIC_DATA_SERVICE_KEY` (커밋 금지)

---

## 진행 단계

### 1단계: 로컬 MVP
- **1-1 뼈대**: `api`/`batch`는 Spring Initializr로 각각 생성(의존성: `api`→`web`, `batch`→`batch-jdbc`) 후 멀티모듈 구조로 재배치, `core`는 직접 작성(`java-library`), 루트 `settings.gradle.kts`/`build.gradle.kts` 배선, `.gitignore`
  - 완료 기준: `./gradlew build` 성공
- **1-2 로컬 인프라**: `compose.yaml`(postgis/postgis:16-3.4, redis:7-alpine, healthcheck), `application.yml`(환경변수 기본값 패턴), Flyway V1~V3
  - 완료 기준: api 기동 시 Flyway V1~V3 적용, `/actuator/health` UP
- **1-3 동작 확인 API**: Region 엔티티·리포지토리, `GET /api/regions` (엔티티 대신 record 응답)
  - 완료 기준: 25개 구 JSON 응답
- **1-4 배치 틀**: Tasklet Job, `runDate` 파라미터, 종료 코드
  - 완료 기준: `./gradlew :batch:bootRun --args='runDate=2026-11-06'` 성공, 같은 날짜 재실행 시 막힘 확인
- **1-5 API 클라이언트**: RestClient로 실거래가 호출, XML/JSON 파싱, 금액 쉼표·공백 처리, 테스트
- **1-6 수집 Step**: 지역 × 월 단위 Chunk Step, S3 대신 로컬 파일로 원본 저장(2단계에서 S3로 교체), upsert, `ingest_status` 갱신
- **1-7 지오코딩**: 캐시 우선 조회, 실패 기록
- **1-8 조회 API**: 기능 1~4, PostGIS 쿼리(`ST_DWithin`, `ST_MakeEnvelope`)
- **1-9 테스트**: Testcontainers(PostGIS)로 리포지토리·배치 통합 테스트

### 2단계: AWS 배포 — Terraform(VPC, RDS, ECS, S3, ECR), GitHub Actions OIDC 배포, EventBridge 스케줄 배치
### 3단계: 확장 + 성능 — 전국·수년치 적재, EXPLAIN 기반 인덱스 튜닝, 파티셔닝, Redis 캐시, k6 전후 측정
### 4단계: 운영·고가용성 — CloudWatch 알람, Multi-AZ, AWS FIS 장애 훈련, 포스트모템

비용 원칙: NAT Gateway 사용 안 함, RDS는 작은 단일 AZ로 시작, AWS Budgets 알람 먼저 설정.

## 진행 현황
- [ ] 1-1 뼈대
- [ ] 1-2 로컬 인프라
- [ ] 1-3 동작 확인 API
- [ ] 1-4 배치 틀
- [ ] 1-5 API 클라이언트
- [ ] 1-6 수집 Step
- [ ] 1-7 지오코딩
- [ ] 1-8 조회 API
- [ ] 1-9 테스트

## 기록 원칙
각 단계가 끝나면 `docs/decisions/`에 짧은 의사결정 기록(문제 / 선택지 / 결정 / 이유 / 결과 수치)을 내가 작성한다. 너는 초안 질문을 던져 내가 정리하도록 돕는다.
