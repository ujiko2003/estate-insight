# DECISIONS

> 지금까지 내린 기술·설계 결정의 기록. 각 항목은 문제 → 검토한 대안 → 결정 → 이유 순으로 적는다.
> 이 프로젝트의 유일한 결정 기록이며, 공개 레포에서 포트폴리오 기록 역할도 한다. 결과 수치가
> 나오면 해당 항목에 함께 적는다. 운영 방식은 13번 참조.
> 마지막 갱신: 2026-11-16

---

## 1. Spring Boot 버전 — 4.1.1 유지

**문제**: Spring Initializr의 버전 메타데이터가 `4.1.1.RELEASE`/`4.1.2.RELEASE` 식으로
`.RELEASE` 접미사를 붙이는데, Boot 4부터는 Maven Central 아티팩트에 그 접미사가 없다. 그 결과
Initializr 웹 UI와 기본 API 호출로는 4.1.1, 4.1.2 둘 다 생성 자체가 500 에러로 실패한다.

**검토한 대안**:
- (A) 최신 패치 버전인 4.1.2로 변경
- (B) 원래 계획값인 4.1.1을 유지하고 우회

**결정**: (B). `starter.zip` 요청 시 쿼리파라미터에 접미사 없이 `bootVersion=4.1.1`을 직접
지정하면 생성된다 (Maven Central에는 접미사 없는 버전만 존재).

**이유**: 이미 계획에 명시된 버전을 바꿀 이유가 없었고, 우회 방법이 간단했다.

---

## 2. 멀티모듈 뼈대 생성 — Initializr + AI 작성 혼합

**문제**: Gradle 멀티모듈 설정(특히 `settings.gradle.kts`, 각 모듈 `build.gradle.kts`)을
처음부터 손으로 작성하는 방식은 실무 관행과 맞지 않고, 보일러플레이트 자체에는 학습 가치가
낮다는 문제가 제기됐다.

**검토한 대안**:
- (A) 전부 손으로 직접 작성
- (B) Spring Initializr로 각 Boot 앱 모듈을 생성한 뒤 멀티모듈로 재배치
- (C) (B)에 더해, 루트 배선(`settings.gradle.kts`, 루트 `build.gradle.kts`)과 각 모듈의
  Gradle 파일 전체까지 AI가 작성

**결정**: (C). `api`/`batch`는 Initializr로 생성(`api`→`web`, `batch`→`batch-jdbc` 의존성),
`core`는 Boot 애플리케이션이 아니라 Initializr 생성 대상이 아니므로 직접(AI가) 작성. 루트
설정과 각 모듈의 의존성 선언은 AI가 담당하고, 엔티티/리포지토리/컨트롤러 등 비즈니스 코드는
예외 없이 개발자가 작성한다.

**이유**: Gradle 보일러플레이트 자체가 학습 목표가 아니다 (학습 목표는 Spring/DB/인프라 동작
원리). 실무에서도 이 보일러플레이트를 손으로 짜는 경우는 드물다.

**버린 대안**: IntelliJ의 "New Module" 마법사로 모듈 생성을 시도한 적이 있는데, 이게 Gradle과
무관한 IntelliJ 네이티브 프로젝트 모듈(`.iml` 파일)을 만들어버려서 Gradle이 해당 모듈을 전혀
인식하지 못했다. 전부 삭제하고 Initializr + 수동 배선 조합으로 재시작했다.

---

## 3. BOM(버전 관리) 적용 방식 — `platform()` 네이티브 방식

**문제**: Spring Boot 4 환경에서 라이브러리 버전을 어떤 방식으로 관리할지. Initializr가
기본으로 생성하는 `build.gradle.kts`는 `io.spring.dependency-management` 플러그인을 쓴다.

**검토한 대안**:
- (A) `io.spring.dependency-management` 플러그인 (Initializr 기본값, Boot 2~3 시절 관행)
- (B) Gradle 5+ 네이티브 `implementation(platform(SpringBootPlugin.BOM_COORDINATES))`

**결정**: (B). Initializr가 생성한 파일에서 `io.spring.dependency-management` 줄은 전부
제거하고 `platform()` 방식으로 교체했다.

**이유**: Gradle이 Maven BOM을 네이티브로 지원하게 된 이후 이 방식이 권장된다. (A)는 프로젝트
전체 configuration에 암묵적으로 적용되어 어디서 버전이 오는지 추적하기 어렵고, 별도 플러그인의
버전까지 관리해야 하는 부담이 있다.

**확인된 제약**: `platform()`은 선언한 configuration에만 적용된다. `annotationProcessor`
configuration은 `implementation`/`api`와 별개의 독립된 해석 경로라서 BOM이 전파되지 않는다 —
Lombok 적용 시 이 문제로 실제 빌드 실패를 겪었다 (5번 항목 참조). `platform()` 없이 버전을
생략한 의존성을 선언하면 아예 해석 자체가 실패한다 (`Could not find ...:.` 형태의 에러로 확인).

---

## 4. `core` 모듈 의존성 scope — `api` vs `implementation` vs `runtimeOnly`

**결정 기준**: "그 타입이 `core`의 public 시그니처(엔티티 클래스, 리포지토리 인터페이스)에
그대로 등장하는가."

- `spring-boot-starter-data-jpa`, `org.hibernate.orm:hibernate-spatial` → **`api`**
  (`@Entity`, `JpaRepository<T, ID>`, `GEOGRAPHY` 매핑용 JTS `Point` 타입이 엔티티의 public
  필드/시그니처에 그대로 노출되기 때문)
- `org.postgresql:postgresql`(JDBC 드라이버) → **`runtimeOnly`**
  (코드에서 타입으로 등장하는 일이 없는 순수 런타임 구현체이기 때문)

---

## 5. Lombok 도입

**문제**: 엔티티의 getter를 직접 작성할지, Lombok으로 자동 생성할지.

**검토한 대안**:
- (A) getter를 직접 작성 (보일러플레이트는 늘지만 동작 원리를 직접 익힐 수 있음)
- (B) Lombok `@Getter` 등 애노테이션으로 자동 생성

**결정**: (B). 실무에서 Lombok 사용 빈도가 매우 높다는 점이 결정 요인이었다.

**겪은 문제와 수정**: Lombok을 처음 `implementation` scope로만 추가했을 때, IntelliJ에서는
작동하는 것처럼 보였지만(IDE 자체 Lombok 플러그인이 에디터 안에서 애노테이션을 시뮬레이션함)
실제 Gradle 빌드(`javac`)에서는 애노테이션 프로세서가 전혀 동작하지 않아 `@RequiredArgsConstructor`
가 무시되고 컴파일 에러가 발생했다 (`variable ... not initialized in the default constructor`).
`compileOnly("org.projectlombok:lombok")` + `annotationProcessor("org.projectlombok:lombok")`
로 수정했고, `annotationProcessor`에도 `platform()`을 별도로 선언해야 했다 (3번 항목의 제약).

이 사례는 "IDE에서 멀쩡히 보이는 것"과 "실제 빌드 도구가 하는 일"이 다를 수 있다는 걸 보여주는
근거로 기록해둔다 — 실제로 프로젝트 초반 여러 차례 비슷한 혼동이 있었다.

---

## 6. 컨트롤러 응답 — 엔티티 대신 DTO(record)

**결정**: 컨트롤러는 항상 엔티티가 아니라 별도의 record(DTO)를 리턴한다. 변환은 응답 레코드의
정적 팩토리 메서드(`from(Entity)`)에서 수행한다.

**이유**:
- DB 스키마 변경과 API 응답 계약을 독립적으로 바꿀 수 있어야 한다. 엔티티를 그대로 리턴하면
  DB에 컬럼을 추가하기만 해도 API 응답 모양이 의도치 않게 바뀐다.
- 연관관계가 있는 엔티티를 직렬화할 때 지연 로딩(LazyInitializationException) 문제를 피한다.

**검토했다가 폐기한 대안**: Spring Data JPA의 DTO 프로젝션 기능을 써서 리포지토리 메서드가
DTO를 직접 리턴하게 만들어 컨트롤러의 변환 코드(`stream().map()`)를 없애는 방법을 시도했다.
그러나 DTO 타입이 `api` 모듈에 있고 리포지토리는 `core` 모듈에 있어서, `core`가 `api`를
참조하게 되는 상황이 발생했다. 이는 멀티모듈 의존 방향 규칙(`core`는 `api`를 알 수 없음)과
정면으로 충돌하며, 실제로 `cannot find symbol` 컴파일 에러로 확인한 뒤 폐기했다. 컨트롤러의
명시적인 변환 코드가 이 프로젝트의 모듈 경계를 지키는 데 필요한 최소한의 접착 코드라고 결론
내렸다.

---

## 7. `sgg_code` 컬럼 타입 — `CHAR(5)` → `VARCHAR(5)`로 변경 (V4 마이그레이션)

**문제**: 최초 스키마(V1)에서 `sgg_code`를 `CHAR(5)`로 선언했는데, Hibernate의 기본 매핑
(`String` + `@Column(length=5)`)은 `VARCHAR`를 기대한다. `ddl-auto: validate` 단계에서
타입 불일치로 애플리케이션 기동이 막혔다 (`wrong column type ... found [bpchar], but
expecting [varchar(5)]`). 참고로 PostgreSQL 공식 문서도 `CHAR(n)`보다 `VARCHAR`/`TEXT` 사용을
권장한다 (성능 이점이 없고 공백 패딩으로 비교 연산에서 버그 소지가 있음).

**검토한 대안**:
- (A) DB 쪽을 `VARCHAR(5)`로 변경하는 새 마이그레이션 추가
- (B) 엔티티 쪽에서 `CHAR` 타입을 명시적으로 매핑

**결정**: (A). `V4__fix_sgg_code_type.sql`을 추가해 `sgg_code`가 쓰인 4개 테이블
(`region`, `apt_complex`, `apt_trade`, `ingest_status`) 모두를 한 번에 `VARCHAR(5)`로
변경했다 — 지금은 `region` 엔티티만 존재해서 당장 문제가 되는 건 `region` 하나뿐이지만, 나머지
세 테이블의 엔티티를 나중에 만들 때 같은 문제가 반복될 것이 예상되어 한 번에 처리했다.

**범위에서 제외한 것**: `apt_trade.trade_key`(`CHAR(64)`), `ingest_status.deal_ym`
(`CHAR(6)`)은 `sgg_code`와 무관한 별도 컬럼이라 이번 변경에 포함하지 않았다
(`OPEN_QUESTIONS.md` 참조).

---

## 8. `batch` 모듈은 Flyway를 실행하지 않는다

**결정**: Flyway 자동 실행 의존성(`spring-boot-starter-flyway`, `flyway-database-postgresql`)
은 `api` 모듈에만 추가한다. `batch`는 JPA와 DB 드라이버는 `core`를 통해 전파받아 쓰지만,
Flyway는 트리거하지 않는다.

**이유**: 스키마 변경 권한을 애플리케이션 하나로 유지해, 두 애플리케이션이 동시에 마이그레이션을
실행하는 상황을 피한다.

**부작용과 해소**: 이 결정 때문에 `batch`에는 한동안(1-2~1-3 구간) DB 연결 설정
(`application.yml`)이 전혀 없었다. 그런데 `batch`가 `core`를 통해 JPA 의존성을 전파받다 보니
Spring Boot가 DataSource를 자동 구성하려고 시도했고, 연결 정보가 없어 `batch` 모듈의 테스트
(`BatchApplicationTests`)가 계속 실패했다. 이 실패는 1-4 작업에서 `batch`에도 DB 연결 정보가
담긴 `application.yml`(Flyway 관련 설정은 제외)을 추가하면서 해소했다.

---

## 9. GitHub 연동 — SSH + 수동 저장소 생성

**결정**: `gh` CLI를 설치하지 않고, 이미 동작 중인 SSH 키(GitHub 인증 사전 확인됨)를 이용해
GitHub 웹 UI에서 빈 저장소를 생성한 뒤 `git remote add` + `git push`로 연동했다.

**이유**: `gh auth login`은 브라우저 상호작용이 필요한 절차인데, SSH 키가 이미 인증을 통과하는
상태라 더 간단한 경로가 있었다. 저장소는 README/`.gitignore`/라이선스 없이 완전히 빈 상태로
생성해야 했다 — 로컬에 이미 커밋이 있는 상태에서 저장소 쪽에 파일이 같이 생성되면 push 시
충돌한다.

---

## 10. 로컬 리소스 충돌 해결 (포트)

로컬 개발 중 다른 사이드 프로젝트와 두 차례 리소스가 충돌했다.

- **PostgreSQL 포트 5432**: 다른 프로젝트의 컨테이너(`rag-pgvector`)가 이미 점유 중이었다.
  호스트 포트를 바꾸는 대안도 있었지만, 그 컨테이너를 중지하는 쪽을 선택했다 (컨테이너 데이터는
  보존되며 `docker start`로 되돌릴 수 있다).
- **API 서버 포트 8080**: 다른 프로젝트(`lexdesk`, Spring AI 애플리케이션)가 장기 구동 중이었다.
  이번엔 반대로 다른 프로젝트를 건드리지 않고, 이 프로젝트의 `api` 포트를 8081로 바꿨다.

두 상황에서 "어느 쪽을 건드릴지"를 매번 다르게 선택한 이유는 상대 프로세스의 성격이 달랐기
때문이다 (일회성으로 떠 있던 컨테이너 vs 29일째 장기 구동 중인 애플리케이션).

---

## 11. 외부 공개용 설계 문서(`docs/pm/overview.html`) 갱신 주기 — 하위 단계 종료마다

**문제**: `overview.html`(외부 공개용 아키텍처/로드맵 문서)을 1단계 하위 단계(1-4, 1-5, ...)가
끝날 때마다 갱신할지, 프로젝트가 어느 정도 마무리된 뒤 한 번에 정리할지 미정이었다
(`OPEN_QUESTIONS.md`에 있었음).

**검토한 대안**:
- (A) 하위 단계 하나가 끝날 때마다 "진행 단계" 체크리스트와 "지금 실제로 돌아가는 것" 스냅샷을
  즉시 갱신
- (B) 1단계 전체(또는 프로젝트 전체)가 끝난 뒤 한 번에 정리

**결정**: (A). 문서 자체의 footer에도 이미 이 정책("1-4 이후 단계가 끝날 때마다 로드맵과
아키텍처 섹션을 갱신한다")이 적혀 있었고, 이를 공식 운영 방식으로 확정한다. 갱신 대상은
"진행 단계" 섹션의 체크리스트 상태(`완료`/`진행 중`/`예정`)와 "지금 실제로 돌아가는 것" 스냅샷
두 곳이다. 아키텍처/DB/스택 섹션은 그 내용 자체가 바뀌는 결정(DECISIONS.md에 새 항목 추가)이
생겼을 때만 같이 갱신한다.

**이유**: (B)를 고르면 "실제로 뭐가 돌아가는지"와 외부 공개 문서 사이의 간극이 몇 주씩 벌어진다
— 이 문서의 가치는 "지금 실제로 동작하는 것"을 정확히 보여주는 데 있으므로(실제로 01번 기능은
`완료`, 나머지는 `예정`으로 세밀하게 구분해둔 것도 같은 이유), 갱신을 미루면 그
가치가 떨어진다. 하위 단계 하나의 작업 단위가 이미 "평일 2~5시간" 수준으로 작으므로(HY.md),
그 끝에 문서 2개 섹션만 갱신하는 추가 비용은 크지 않다.
**담당**: PM(내)이 BACKLOG.md의 각 하위 단계 완료 확인 시점에 맞춰 갱신한다. Hy가 직접 손대지
않는다 (`overview.html`은 `docs/pm/`에 있는 PM 산출물).

---

## 12. 배치 종료 코드 — 별도 `ExitCodeGenerator` 없이 진행

**문제**: `SpringApplication.exit()`가 Job 실행 결과를 프로세스 종료 코드로 바꿔주는지, 직접
`ExitCodeGenerator` 빈을 등록해야 하는지 Boot 4.1.1 / Batch 6 조합에서 확인되지 않았다
(`OPEN_QUESTIONS.md`에 있었음). 종료 코드는 ECS/EventBridge가 실패를 감지하는 근거다.

**검토한 대안**:
- (A) `System.exit(SpringApplication.exit(...))`만 적용
- (B) `ExitCodeGenerator` 빈을 직접 등록

**결정**: (A).

**이유**: 실행 결과 Job 성공 시 0, Tasklet 예외로 인한 실패 시 5가 나왔다. 실패를 0이 아닌 값으로
알린다는 요구를 (A)만으로 충족하므로 코드를 더 넣을 이유가 없다.

**함께 확인한 것**: 같은 `runDate` 재실행은 `JobInstanceAlreadyCompleteException`으로 거부되고
종료 코드 1로 끝난다. 처음 DoD에는 `(JOB_NAME, JOB_KEY)` UNIQUE 제약으로 막힌다고 적었으나,
실제로는 그 전에 완료된 JobInstance 확인 단계에서 거부되어 DoD 문구를 고쳤다.

---

## 13. 결정 기록 방식 — `DECISIONS.md` 단일화, 커밋 메시지 "결정:" 줄

**문제**: 결정 기록이 `docs/decisions/`(단계별 개발자 기록)와 이 문서로 나뉘어 있었고, 둘의 역할
분담이 정해지지 않았다(`OPEN_QUESTIONS.md`에 있었음). PM이 어떤 변경을 Hy의 결정으로 보고 기록할지
기준도 없었다.

**검토한 대안**:
- (A) `docs/decisions/`를 별도로 유지하고 이 문서는 운영·아키텍처 수준만 담당
- (B) 이 문서 하나로 통합

**결정**: (B). Hy가 2026-11-10 결정.
- `docs/decisions/`는 쓰지 않는다. 이 문서가 유일한 결정 기록이다.
- 형식은 문제 → 대안 → 결정 → 이유를 유지하고, 결과 수치가 나오면 함께 적는다.
- Hy의 결정은 커밋 메시지의 `결정:` 줄로 남긴다. PM은 그 줄을 Hy의 결정으로 보고 이 문서에 기록한다.
- 결정 흔적이 보이는데 `결정:` 줄이 없으면 PM이 기록하기 전에 Hy에게 묻는다.

**이유**: 이 문서는 공개 레포에 있어 포트폴리오 기록 역할을 함께 할 수 있으므로, 결정 기록을 한 곳에
모은다.

---

## 14. 실거래가 응답 변환 — 금액 검증, 변환 클래스 분리, 테스트 XML 위치

**문제**: API의 `dealAmount`는 `335,000`처럼 쉼표·공백이 섞인 문자열이고, DB `deal_amount`는 만원
단위 `INTEGER NOT NULL`이다. 값이 비었거나 이상할 때의 처리, 변환 코드의 위치, 파서 테스트에 쓸 XML의
보관 방식이 정해지지 않았다.

**검토한 대안**:
- 이상값 처리: (A) 예외 (B) `null` 반환 (C) 0으로 변환
- 변환 위치: (A) 파서 안 (B) 별도 클래스 (C) `Item` record 메서드
- 테스트 XML: (A) `src/test/resources` 파일 (B) 테스트 코드 안 텍스트 블록

**결정**: Hy가 2026-11-11 결정 (커밋 `f25bc0d`의 `결정:` 줄).
- 금액이 비었거나 숫자가 아니면 `IllegalArgumentException`을 던진다.
- 금액이 0 이하이면 `IllegalArgumentException`을 던진다.
- 변환은 파서와 분리된 별도 클래스(`AptTradeFieldParser`)에 둔다.
- 실제 응답은 `src/test/resources` 파일로, 작은 케이스는 테스트 안 텍스트 블록으로 둔다.

**이유**:
- 이상값 예외: 0이나 `null`로 넘기면 통계가 조용히 틀어지거나 1-6에서 다시 처리해야 한다.
- 0 이하 예외: 거래가 이루어진 매물의 금액은 0 이하일 수 없다.
- 클래스 분리: 1-6의 날짜·면적 변환을 한곳에 모으고, XML 없이 테스트하기 위해.
- XML 위치: 실제 데이터 기준으로 검증하면서, 작은 케이스는 테스트 안에서 바로 읽히게.

**결과 수치**: 단위 테스트 17건 통과(금액 12, 파서 5), Spring 컨텍스트 없이 실행. 실제 응답
(강남구 2026-09) 125건의 금액 전부 변환, 실패 0건.

---

## 15. 배치 메타 테이블 자동 생성 — `initialize-schema: never` 명시

**문제**: Spring Batch 메타 테이블은 Flyway V2로 만든다. `CLAUDE.md`와 V2 SQL 주석은
`spring.batch.jdbc.initialize-schema: never`를 전제로 하지만, `batch/application.yml`에는 이 설정이
없었다. 기본값 `embedded`는 내장 DB에서만 테이블을 만들기 때문에 PostgreSQL에서는 우연히 문제가 없던
상태였다(`OPEN_QUESTIONS.md`에 있었음).

**검토한 대안**:
- (A) 기본값(`embedded`)에 맡긴다 — 지금 동작은 같지만, 문서와 코드가 어긋난 채로 남는다.
- (B) `never`를 명시한다.

**결정**: (B). 2026-11-11, Hy가 PM 판단에 위임해 PM이 결정.

**이유**:
- 스키마 변경 권한은 Flyway(`api` 모듈) 하나에 둔다는 8번 결정과 같은 방향이다. Boot가 메타 테이블을
  만들 수 있는 경로를 설정으로 닫아 둔다.
- 테스트나 실행 환경이 바뀌어도(예: 내장 DB) Flyway V2와 다른 메타 테이블이 생길 여지가 없다.
- 동작 변화 없이 문서·V2 주석과 실제 설정이 일치한다.

**확인 방법**: 설정 추가 후 `./gradlew :batch:bootRun --args='runDate=...'`가 기존처럼 성공.

---

> 16~20번은 Hy가 2026-11-10 `docs/decisions/`에 쓴 기록(커밋 `aface3c`)을 13번 결정에 따라
> 2026-11-11 이 문서로 옮긴 것이다. 결정 시점은 1-4, 1-5 진행 중이다. 1-4 기록의 종료 코드 부분은
> 12번과 같은 내용이라 옮기지 않았다.

## 16. 배치 실행 식별 — incrementer 없이 `runDate` 파라미터

**문제**: 수집 Job은 정기적으로 실행된다. 같은 날짜를 두 번 돌려도 중복 수집이 되면 안 되고, 실패한
날짜는 다시 돌릴 수 있어야 한다.

**검토한 대안**:
- (A) `RunIdIncrementer` 사용 — 실행마다 번호가 붙어 항상 새 JobInstance가 된다.
- (B) incrementer 없이 `runDate` 파라미터로 실행 — 날짜가 JobInstance를 결정한다.

**결정**: (B). `runDate=2026-11-06` 형식으로 실행한다.

**이유**: (A)는 같은 날짜를 다시 돌려도 새 작업으로 취급되어 중복 실행을 막지 못하고, 실행 시 넘긴
파라미터도 무시된다. (B)는 Spring Batch의 메타 테이블이 "이 날짜를 이미 성공했는가"를 판단해 준다.

**결과 수치**:

| 실행 | JobInstance | JobExecution | 결과 |
|---|---|---|---|
| `2026-12-02` 실패 후 재실행 | 1개 유지 | 1개에서 2개로 증가 | FAILED 후 COMPLETED |
| `2026-11-06` 성공 후 재실행 | 변화 없음 | 변화 없음 | `JobInstanceAlreadyCompleteException`, Tasklet 실행 안 됨 |

---

## 17. API 호출과 파싱 분리

**문제**: API 응답은 XML이다. 호출하면서 바로 객체로 받을 수도 있고, 문자열로 받은 뒤 따로 변환할
수도 있다.

**검토한 대안**:
- (A) `RestClient`로 호출하면서 XML을 객체로 바로 변환
- (B) 호출 부품은 XML 원본 문자열을 반환하고, 파싱 부품이 객체로 변환

**결정**: (B). `AptTradeApiClient`와 `AptTradeXmlParser`로 나눈다.

**이유**:
- 1-6에서 원본 응답을 파일로 저장해야 한다. 원본이 있으면 파싱 버그를 고칠 때 API를 다시 호출하지
  않아도 된다.
- 파서는 문자열만 받으므로 네트워크와 인증키 없이 테스트할 수 있다.
- 네트워크·인증 문제와 데이터 형식 문제가 서로 다른 부품에서 드러난다.

**결과 수치**: 파서 단위 테스트 5건이 네트워크·Spring 없이 통과(14번 참조).

---

## 18. 이미 인코딩된 인증키 전송 — `UriComponentsBuilder.build(true)`

**문제**: 포털의 일반 인증키에는 `%2B`, `%3D` 같은 인코딩 문자가 들어 있다. `RestClient`에 문자열
URI를 넘기면 값이 다시 인코딩되어(`%`가 `%25`로 변환) 인증에 실패할 수 있다.

**검토한 대안**:
- (A) 문자열로 URI를 넘겨 `RestClient`가 인코딩하게 한다.
- (B) Decoding 키를 쓰고 `RestClient`가 인코딩하게 한다.
- (C) `UriComponentsBuilder.build(true)`로 인코딩이 끝난 `URI` 객체를 만들어 넘긴다.

**결정**: (C).

**이유**: (A)는 이중 인코딩이 생긴다. (C)는 포털에서 받은 키를 그대로 환경변수에 넣어 쓸 수 있고,
"이미 인코딩된 값"이라는 의도가 코드에 드러난다. URI에 인증키가 포함되므로 URI 전체를 로그로 남기지
않는다.

**결과 수치**: `./gradlew :batch:bootRun --args='runDate=2026-12-05'`로 실제 호출, `resultCode` 000,
Step 실행 224ms.

---

## 19. 응답 모델 — XML 모양 record와 결과 record 분리

**문제**: 응답에는 값 없음이 공백 한 칸으로 오거나(`<aptDong> </aptDong>`), 앞자리 0이 의미 있는 값
(`<bubun>0003</bubun>`), 쉼표가 든 금액(`335,000`)이 섞여 있다. XML 계층도 깊고 중간 값이 null일 수 있다.

**검토한 대안**:
- (A) JPA 엔티티로 바로 변환
- (B) `Item`을 숫자·날짜 타입으로 선언
- (C) XML 모양 record(`AptTradeResponse`, `Item` 필드는 전부 `String`)와 검사를 마친 결과
  record(`AptTradePage`)로 분리

**결정**: (C). `Item` 필드는 25개다.

**이유**:
- XML 구조와 DB 구조는 다르다. 예를 들어 `dealYear`, `dealMonth`, `dealDay`는 DB에서 `deal_date` 하나가
  된다.
- 전부 `String`으로 받으면 원본 값이 유지되고, 변환 규칙을 한곳에 모을 수 있다(14번의 별도 변환
  클래스로 이어짐).
- `AptTradePage`를 쓰는 쪽은 XML 구조와 null 처리를 몰라도 된다.

**결과 수치**: 강남구 2026년 9월 응답 `totalCount` 125건을 전부 읽음.

---

## 20. 비정상 응답 구분 — 변환 후 직접 검사

**문제**: 공공 API의 인증 실패 응답은 성공 응답과 XML 구조 자체가 다르다
(`<OpenAPI_ServiceResponse><cmmMsgHeader>...`). Jackson은 이 XML을 받아도 예외를 내지 않고 `header`와
`body`가 null인 객체를 만든다.

**검토한 대안**:
- (A) XML 변환 예외에만 의존
- (B) 변환 후 `header`, `resultCode`, `body`, `totalCount`를 직접 검사

**결정**: (B). 비정상이면 `IllegalStateException`을 던지고, 거래가 없는 정상 응답은 빈 목록으로 처리한다.

**이유**: (A)는 인증 실패를 거래 0건으로 착각하거나 원인을 알 수 없는 NPE로 끝난다. 수집이 조용히 비어
버리는 상황을 막아야 한다.

**결과 수치**: `resultCode` 오류, 접근거부, `body` 누락, `totalCount` 누락 4종 모두 `IllegalStateException`
으로 처리됨을 확인. 이 중 `resultCode` 오류와 접근거부는 단위 테스트로 커밋됨. 실제 0건 응답
(강남구 2026-12)은 빈 목록으로 읽힘.

---

## 21. 수집 Chunk의 한 건 — 지역×계약월, chunk size 1

**문제**: 1-6 수집 Step을 Chunk 방식으로 만들 때, Reader가 내보내는 한 건을 거래 1건으로 볼지 지역×계약월
1건으로 볼지 정해야 했다. 원본 파일 저장과 `ingest_status`(PK: 지역, 계약월)는 지역×월 단위다.

**검토한 대안**:
- (A) 거래 1건 단위 — Reader가 API 페이지를 넘기며 거래를 하나씩 읽는다.
- (B) 지역×계약월 1건 단위, chunk size 1 — 한 달을 한 트랜잭션으로 처리한다.

**결정**: (B). Hy가 2026-11-11 결정 (커밋 `b3f61eb`의 `결정:` 줄).

**이유**: `ingest_status`의 PK(지역, 계약월)와 재시작·수집 이력 단위를 일치시키기 위해.

**결과 수치**: 더미 3건(3개월)으로 `collectStep` read 3 / write 3 / commit 3 — 한 달마다 커밋이 한 번씩
일어나는 것을 확인. 실제 수집 수치는 1-6 완료 후 기록.

---

## 22. `logTestJob` 삭제 — 수집 Job 하나만 둔다

**문제**: 수집 Job(`aptTradeDataJob`)을 추가하면 Job 빈이 2개가 되어, Boot가 실행할 Job을 스스로 정하지
못한다.

**검토한 대안**:
- (A) `logTestJob` 삭제
- (B) 유지하고 실행 때마다 `spring.batch.job.name` 지정

**결정**: (A). Hy가 2026-11-11 결정 (커밋 `b3f61eb`의 `결정:` 줄).

**이유**: Job이 2개면 실행할 때마다 Job 이름을 지정해야 한다. 1-4의 확인 결과는 12·16번과 커밋 이력에
남아 있다.

---

## 23. 수집 대상 계약월 — `runDate` 기준 최근 3개월

**문제**: 수집 Job이 실행될 때 어떤 계약월을 받을지 정해야 했다. 실거래 신고는 계약 후 30일 안에 하면
되고, 해제·등기 정보는 그 뒤에 붙는다. 즉 최근 몇 달의 데이터는 계속 바뀐다.

**검토한 대안**:
- (A) `runDate` 기준 최근 N개월을 자동 계산
- (B) `fromYm`, `toYm` 파라미터로 명시

**결정**: (A), N=3. `runDate`가 속한 달을 포함해 3개월(예: `runDate=2026-11-12` → 202609, 202610, 202611).
이 범위의 계약월은 이전에 `DONE`이었어도 다시 `PENDING`으로 돌려 재수집하고, 범위 밖은 건드리지 않는다.
N은 코드에 고정하지 않고 설정값으로 둔다. 2026-11-12, Hy가 PM 판단에 위임해 PM이 결정.

**이유**:
- 신고 기한 30일 때문에 M월 거래는 M+1월 말이 되어야 거의 다 들어오고, 해제 신고는 그보다 늦게 올 수
  있다. 2개월은 경계에 걸리고, 3개월이면 여유가 있다(CLAUDE.md의 "최근 2~3개월 매일 재수집").
- 2단계에서 EventBridge가 매일 `runDate`만 넘겨 실행하면 되므로 스케줄 설정이 단순하다.
- `runDate`가 JobInstance를 정한다는 16번 결정과 맞다. 날짜가 바뀌면 새 실행이 되고, 같은 계약월을 다른
  `runDate`로 다시 받는 것은 정상 동작이다.
- 호출량은 활성 지역 1개 × 3개월 = 하루 3회 수준으로 한도에 영향이 없다.

**뒤로 미룬 것**: 수년치 적재(3단계)는 (B)를 그때 추가한다.

**확정과 결과**: Hy가 커밋 `469f77d`의 "결정:" 줄로 확정했다(검토한 대안: 당월 제외). `PrepareTargetsTasklet`에
구현. `runDate=2026-11-12` → 11680 × 202609·202610·202611 `PENDING` 3행, 이어서 `runDate=2026-12-01` →
202610·202611 덮어씀, 202612 추가, 범위 밖 202609는 그대로(총 4행). PM이 DB에서 대조. N은 커밋 `40fd2ac`에서 설정값
`collect.target-months`(기본 3)로 분리했다(27번).

---

## 24. 거래 변환 실패 — 그 지역×계약월 전체를 `FAILED`

**문제**: 한 달치 거래 중 한 건이라도 변환에 실패하면(예: 14번의 금액 예외) 그 달을 어떻게 처리할지
정해야 했다.

**검토한 대안**:
- (A) 그 지역×월 전체를 저장하지 않고 `FAILED` + `last_error` 기록, 다음 실행에서 재시도
- (B) 실패한 거래만 건너뛰고 나머지 저장, 건너뛴 수 기록

**결정**: (A). 한 달이 실패해도 다른 달의 수집은 계속한다. 2026-11-12, Hy가 PM 판단에 위임해 PM이 결정.

**이유**:
- 21번 결정으로 한 달이 한 트랜잭션이므로, 실패하면 그 달 전체가 롤백되어 일부만 저장되는 상태가 생기지
  않는다.
- `total_count = saved_count`가 항상 "그 달을 빠짐없이 받았다"는 뜻으로 유지된다. (B)는 차이가 날 때마다
  원인을 따로 추적해야 한다.
- 14번 결정(이상값을 조용히 넘기지 않는다)과 같은 방향이다.
- 23번 결정으로 최근 3개월은 매일 다시 받으므로, 일시적 오류는 다음 실행에서 자연히 재시도된다.

**주의**: `last_error`에는 실패한 필드와 값만 남기고 요청 URL은 넣지 않는다(인증키 포함). 실제 데이터에서
같은 달이 반복해서 실패하면 그때 (B)를 다시 검토한다.

---

## 25. `ingest_status` 갱신 — JDBC `INSERT ... ON CONFLICT`

**문제**: 대상 준비 단계(`prepareTargetsStep`)는 지역×계약월 행이 없으면 만들고, 있으면 `PENDING`으로
되돌려야 한다. 같은 계약월이 매일 다시 대상이 되므로(23번) 여러 번 실행해도 행이 늘면 안 된다.

**검토한 대안**:
- (A) JPA로 조회한 뒤 `save`(없으면 insert, 있으면 update)
- (B) JDBC `INSERT ... ON CONFLICT (sgg_code, deal_ym) DO UPDATE`

**결정**: (B). 갱신 시 `status = 'PENDING'`, `last_error = NULL`. 2026-11-16, Hy(커밋 `469f77d`).

**이유**: 재실행해도 행이 중복되지 않고, 한 문장으로 DB 안에서 처리된다. 조회 후 저장은 SQL이 두 번 나가고
엔티티(`deal_ym CHAR(6)` 매핑 문제 포함)가 필요하다.

**결과**: 같은 계약월(202610·202611)을 두 `runDate`로 두 번 준비해도 행이 1개씩 유지(PM이 DB에서 대조).
거래 upsert(브리프 1-6의 결정 3)도 같은 방식으로 갈지는 upsert 작업 때 정한다.

---

## 26. batch 모듈에도 Lombok 추가

**문제**: batch 모듈의 Tasklet 등에 생성자 주입 코드가 반복된다. `core`의 Lombok은 `compileOnly`라서
batch로 전파되지 않는다.

**검토한 대안**:
- (A) 생성자를 직접 작성
- (B) batch에도 Lombok 추가(`compileOnly` + `annotationProcessor`, 프로세서에는 Boot BOM을 따로 적용)

**결정**: (B). `@RequiredArgsConstructor`로 생성자 주입. 2026-11-16, Hy(커밋 `469f77d`).

**이유**: 생성자 주입 보일러플레이트를 없앤다. 모듈 간 스타일을 `core`와 맞춘다.

---

## 27. 수집 설정값 — `@ConfigurationProperties` record

**문제**: 23번 결정에 따라 수집 개월 수 N을 설정값으로 빼야 했다. 그런데 Tasklet의 생성자는 Lombok
`@RequiredArgsConstructor`(26번)가 만들기 때문에, 필드에 붙인 `@Value`가 생성자 파라미터로 복사되지 않는다.

**검토한 대안**:
- (A) `lombok.config`에서 `@Value`를 생성자로 복사하도록 허용
- (B) Lombok을 빼고 생성자를 직접 작성
- (C) `@ConfigurationProperties(prefix = "collect")` record(`CollectProperties`)로 받아 주입

**결정**: (C). `collect.target-months: 3`, `BatchApplication`에 `@ConfigurationPropertiesScan`.
2026-11-16, Hy(커밋 `40fd2ac`).

**이유**: Lombok 설정을 건드리지 않고 생성자 주입을 유지한다. 수집 관련 설정이 늘면 한곳에 모을 수 있다.

**결과**: `runDate=2027-02-10` → 202612·202701·202702(연도 경계 정상), `runDate=2027-06-10
--collect.target-months=2` → 202705·202706만 생성. 명령줄 값이 yml보다 우선하고 Job 파라미터에는 `runDate`만
남는다(PM이 `batch_job_execution_params` 16·17번에서 대조). 확인용 미래 월 행은 Hy가 삭제.

---

## 28. 수집 Step의 Reader 범위 — 이번 `runDate`의 대상 월만

**문제**: `prepareTargetsStep`은 범위 밖의 달을 건드리지 않으므로(23번), 이전 실행에서 `PENDING`으로 만든 달이
범위를 벗어난 뒤에도 `PENDING`으로 남을 수 있다(예: `runDate=2026-12-01` 실행 후의 202609). `collectStep`의
Reader가 무엇을 읽을지 정해야 했다.

**검토한 대안**:
- (A) 이번 `runDate`에서 계산한 대상 월(활성 지역 × N개월)만 읽는다
- (B) `ingest_status`에서 `PENDING`(·`FAILED`)인 행을 전부 읽는다

**결정**: (A). 대상 월 목록은 `prepareTargetsStep`에서 Job `ExecutionContext`로 넘겨 Reader가 사용하는 방식을
권장(구현 방식은 Hy가 작성하며 확정). 2026-11-16, Hy.

**이유**:
- (B)는 범위를 벗어난 달까지 다시 받아 23번의 "범위 밖은 건드리지 않는다"와 어긋나고, 한 번의 실행이 무엇을
  처리할지가 그 전까지의 실행 이력에 따라 달라진다.
- (A)는 한 `runDate` 실행이 처리하는 범위가 `runDate`와 설정만으로 정해진다(16번과 같은 방향).
- 매일 실행하면 범위에서 빠지는 달은 전날까지 이미 수집된 달이므로, 남는 `PENDING` 행은 "그 뒤로 대상이 아니었다"는
  기록일 뿐 데이터 누락이 아니다.

**뒤로 미룬 것**: 범위 밖에 남은 `FAILED` 달을 다시 받는 방법(수동 재수집 파라미터 등)은 3단계의 기간 지정
수집(23번 (B))을 만들 때 함께 다룬다.

---

## 29. `trade_key` 입력 — 변환한 값을 정규화해서 해시

**문제**: API에 거래 ID가 없어 자연키 해시(`trade_key`)로 upsert한다(CLAUDE.md). 그런데 원본 값은 표기가
일정하지 않다. 값 없음은 공백 한 칸(`aptDong` 125건 중 86건)이고, 면적은 `60`과 `84.93`처럼 자릿수가 섞여 오며,
금액에는 쉼표가 있다. 원본 문자열을 그대로 해시하면 표기만 달라도 같은 거래가 다른 키가 된다.

**검토한 대안**:
- (A) 원본 문자열을 strip해서 이어 붙여 해시
- (B) 변환한 값을 정해진 형식으로 이어 붙여 해시

**결정**: (B). 형식은 다음과 같다.
- 입력 순서: `aptSeq`, 계약일, 층, 전용면적, 금액, 동
- 값 형식: 계약일 ISO(`2026-09-30`), 면적 scale 2(`60.00`), 금액·층 정수, null은 `""`
- 구분자 `|`로 이어 UTF-8 SHA-256 → 소문자 hex 64자
- 해제 여부는 넣지 않는다(CLAUDE.md)

2026-11-17, Hy(PM 의견 채택).

**이유**:
- 키 규칙이 변환 규칙 위에 서므로 표기 차이(`60`/`60.00`, 쉼표, 공백)에 흔들리지 않는다.
  `BigDecimal`은 `60`과 `60.00`을 `equals`로 다르게 보므로 scale을 고정한다.
- 구분자가 없으면 층 `1`+면적 `12.00`과 층 `11`+면적 `2.00`처럼 이어 붙인 결과가 같아질 수 있다.

**확인 방법**(briefs/1-6-3.md): 동 null과 `" "`, 면적 `60`과 `60.00`은 같은 키, 층만 다르면 다른 키, 실제 응답
125건에서 서로 다른 키 125개.

---

## 30. 변환 결과 모델 — 단지 record와 거래 record 분리

**문제**: 응답 `Item` 하나에 단지 정보와 거래 정보가 함께 있다. 저장 대상은 `apt_complex`와 `apt_trade` 두 테이블이고,
FK 때문에 단지를 먼저 넣어야 한다. 실제 응답 125건에 단지는 80개다.

**검토한 대안**:
- (A) 단지 필드까지 담은 거래 record 하나 — Writer가 단지를 골라내야 한다
- (B) Item 하나에서 단지 record와 거래 record를 함께 만든다

**결정**: (B). 필드 단위 변환은 `AptTradeFieldParser`(14번)에 두고, Item을 두 record로 조립하는 클래스를 따로 둔다.
Writer는 단지를 `aptSeq`로 중복 제거해 먼저 upsert하고 거래를 upsert한다. 2026-11-17, Hy.

**이유**: 테이블 구조와 모양이 같아 upsert SQL이 record에 그대로 대응한다. 단지 중복 제거가 Writer의 명시적인
한 단계가 된다.

---

## 31. 단지 주소 형식 — 응답만으로 조립

**문제**: `apt_complex.jibun_address`, `road_address`를 채워야 한다. 원본은 `umdNm=대치동`, `jibun=1014-3`,
`roadNm=도곡로78길`, `roadNmBonbun=00022`, `roadNmBubun=00000`처럼 나뉘어 온다. 시·도와 시군구 이름은 응답에 없고
`region` 테이블에 있다.

**검토한 대안**:
- (A) 응답만으로 조립: `대치동 1014-3`, `도곡로78길 22`(앞자리 0 제거, 부번 0이면 생략)
- (B) `region`을 조회해 전체 주소로 조립: `서울특별시 강남구 대치동 1014-3`

**결정**: (A). 2026-11-17, Hy.

**이유**:
- 변환기가 DB 없이 단위 테스트된다.
- 시·도와 시군구 이름은 1-7 지오코딩 때 `region`과 합쳐 쓴다.
- 번지를 다시 나눠야 할 일이 생겨도 원본 응답 파일(1-6 원본 저장)에 `bonbun`/`bubun`이 남는다. 이 결정으로
  OPEN_QUESTIONS의 "주소 통합 저장이 충분한가"를 닫는다.
