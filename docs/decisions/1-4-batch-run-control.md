# 1-4 배치 틀: 실행 식별과 종료 코드

## 1. 같은 날짜의 중복 실행을 어떻게 막을 것인가

**문제**
수집 Job은 정기적으로 실행된다. 같은 날짜를 두 번 돌려도 중복 수집이 되면 안 되고, 실패한 날짜는 다시 돌릴 수 있어야 한다.

**선택지**
- (A) `RunIdIncrementer` 사용: 실행마다 번호가 붙어 항상 새 JobInstance가 된다.
- (B) incrementer 없이 `runDate` 파라미터로 실행: 날짜가 JobInstance를 결정한다.

**결정**
(B). `runDate=2026-11-06` 형식으로 실행한다.

**이유**
(A)는 같은 날짜를 다시 돌려도 새 작업으로 취급되어 중복 실행을 막지 못하고, 실행 시 넘긴 파라미터도 무시된다. (B)는 Spring Batch의 메타 테이블이 "이 날짜를 이미 성공했는가"를 판단해 준다.

**결과 수치**

| 실행 | JobInstance | JobExecution | 결과 |
|---|---|---|---|
| `2026-12-02` 실패 후 재실행 | 1개 유지 | 1개에서 2개로 증가 | FAILED 후 COMPLETED |
| `2026-11-06` 성공 후 재실행 | 변화 없음 | 변화 없음 | `JobInstanceAlreadyCompleteException`, Tasklet 실행 안 됨 |

## 2. Job 실패를 운영 환경에 어떻게 알릴 것인가

**문제**
ECS와 EventBridge는 로그가 아니라 프로세스 종료 코드로 실패를 감지한다. `SpringApplication.run()`만 호출하면 Job이 FAILED여도 예외가 `main`까지 오지 않아 정상 종료로 보인다.

**선택지**
- (A) `System.exit(SpringApplication.exit(SpringApplication.run(...)))`
- (B) `ExitCodeGenerator` Bean을 직접 등록

**결정**
(A). 별도 Bean 없이 Spring Boot의 동작을 사용한다.

**이유**
Boot 4.1.1 + Batch 6 환경에서 (A)만으로 실패가 0이 아닌 값으로 나오는 것을 실행으로 확인했다.

**결과 수치**

| 상황 | 멈춘 지점 | 종료 코드 |
|---|---|---|
| Job 성공 | 끝까지 실행 | 0 |
| 이미 성공한 날짜 재실행 | Job 시작 전 예외, `main`까지 전파 | 1 |
| Tasklet 예외로 Job FAILED | Step이 예외 처리 후 Job 완료 | 5 |

## 남은 문제

- `@SpringBootTest`인 `BatchApplicationTests`가 Spring을 띄울 때 실제 Job도 실행되어, 파라미터 없는 FAILED 기록이 로컬 DB에 쌓인다. Job이 실패해도 테스트는 통과한다. 수집 로직을 붙이기 전에 처리가 필요하다.
- `runDate` 누락 시 Job 시작 후 NPE로 실패한다. 실제 수집 Job에서는 시작 전 파라미터 검증(`JobParametersValidator`)을 검토한다.
