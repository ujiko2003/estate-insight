# DECISIONS

> 지금까지 내린 기술·설계 결정의 기록. 각 항목은 문제 → 검토한 대안 → 결정 → 이유 순으로 적는다.
> 단계별 상세 작업 기록(`docs/decisions/`)과는 별도로, 프로젝트 운영·아키텍처 수준의 결정만 모은다.
> 마지막 갱신: 2026-11-09

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
