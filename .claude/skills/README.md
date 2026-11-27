# 백엔드 코딩 규칙 스킬

> **이 폴더는 에이전트가 읽습니다.** 이 저장소(`bueongi-backend`)의 Kotlin 파일을 고칠 때 적용됩니다.
> 저장소 루트가 곧 백엔드입니다 — `core/`·`storage/`·`clients/`·`support/` 가 루트 바로 아래에 있습니다.

Kotlin 1.9.25 + Spring Boot 3.4.0 + Java 21, Gradle 멀티모듈 백엔드의 규칙이다. 패키지 루트는 `io.soo.bueongi` 다.

**규칙의 정본은 저장소 [README.md](../../README.md) 「코드 작성 규칙」(1~18조)이다.** 이 스킬들은 그 조문을
코드 수준으로 풀어 쓴 것이고, 둘이 어긋나면 README 를 기준으로 삼되 어긋남을 보고한다.
과설계 방지(YAGNI)는 사용자 레벨 `ponytail` 스킬이 담당하며 백엔드 작업에도 함께 적용된다.

**18종을 다 읽지 않는다.** 아래 "항상" 넷을 읽고, 나머지는 지금 고치는 파일이 무엇인지에 따라 골라 읽는다.

## 이 저장소의 사실 — 예시를 옮길 때 이것에 맞춘다

| 항목        | 이 저장소                                                                                                               |
|-------------|-------------------------------------------------------------------------------------------------------------------------|
| 부팅 모듈   | `core/core-app` 하나뿐 (`bootRun`·`bootJar`·`@WebMvcTest`·`@SpringBootTest` 는 여기서만)                                |
| 도메인 모듈 | `core/core-<도메인>` — `route`·`share`·`place`·`safety-spot`·`admin-auth`·`admin-safety-spot`                           |
| 공용        | `core/core-common`(`ApiException`·`ApiControllerAdvice`·`RoutePlannedHandler`), `core/core-enum`(여러 모듈이 쓰는 enum) |
| 저장        | `storage/db-<이름>` — `db-safety-spot`(패키지 `io.soo.bueongi.storage.db.safetyspot`), `db-share`(Redis)                |
| 외부 연동   | `clients/client-*` (core-common 을 의존하지 않는다)                                                                     |
| 지원        | `support/logging`·`support/util`                                                                                        |
| DB          | **PostgreSQL 16 + PostGIS**, 스키마는 Flyway(`storage/db-<이름>/src/main/resources/db/migration/`)                      |
| 에러 응답   | Spring `ProblemDetail` + `code` 속성. 응답 래퍼(`{result, data, error}`)는 없다                                         |
| 테스트      | JUnit5 + AssertJ + **mockito-kotlin 5.2.1**. 컨텍스트·DB 테스트는 `@Tag("context")`, `unitTest` 태스크가 그것을 뺀다    |
| 프로파일    | 기본(default) + `local`(`application-local.yml`). 비밀값은 환경변수·`.env.local`                                        |
| URL         | README 18조 — 자원 복수형·하이픈, 동사 금지. 기존 경로는 승인 전까지 유지, `/api/v1` 버저닝은 요구하지 않는다           |

## 항상

| 스킬                                                  | 다루는 것                                                                      |
|-------------------------------------------------------|--------------------------------------------------------------------------------|
| [kotlin-common](kotlin-common/SKILL.md)               | 자료형, 불변성, 조건문 풀어쓰기, 이름, 예외, 주석, 검증                        |
| [kotlin-abstraction](kotlin-abstraction/SKILL.md)     | 이름 짓기와 추상화 레벨 — 함수를 어떻게 쪼개고 무엇이라 부를지, 어느 층에 둘지 |
| [kotlin-module-layout](kotlin-module-layout/SKILL.md) | 새 파일을 어느 모듈·패키지에 둘지, 의존 방향, 도메인 경계                      |
| [kotlin-test](kotlin-test/SKILL.md)                   | 한 메서드 = 한 기능, mockito-kotlin 범위, 계층별 테스트 대상, `context` 태그   |

## 기능 하나를 만들 때 — 아래에서 위로

새 도메인은 이 순서로 만든다. 아래에서 위로 쌓아야 상위 계층이 이미 있는 것을 조립하게 된다.

| #   | 스킬                                                    | 언제                                                                                                   |
|-----|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| 1   | [kotlin-entity](kotlin-entity/SKILL.md)                 | 테이블·상태 변경 — `@Entity`, `protected set` + 행위 메서드, 애그리게이트 (JPA 를 쓰는 storage 모듈만) |
| 2   | [kotlin-migration](kotlin-migration/SKILL.md)           | **테이블을 바꾸면 반드시 함께** — PostgreSQL/PostGIS Flyway 파일 이름·자리, 무중단 배포 순서           |
| 3   | [kotlin-repository](kotlin-repository/SKILL.md)         | 쿼리 추가, 공간 쿼리, 대량 적재 — Store 인터페이스, 페이징, N+1, `batchUpdate`                         |
| 4   | [kotlin-implement](kotlin-implement/SKILL.md)           | 조회·저장 상세 — `Finder`·`Appender` 재사용 단위, 저장 모델↔도메인 변환                                |
| 5   | [kotlin-domain-service](kotlin-domain-service/SKILL.md) | 업무 흐름 — 유스케이스 조립, `@Transactional` 경계                                                     |
| 6   | [kotlin-dto](kotlin-dto/SKILL.md)                       | 요청·응답 형태 — `Request`·`Response` 이름 규약, 검증 애너테이션                                       |
| 7   | [kotlin-controller](kotlin-controller/SKILL.md)         | 엔드포인트 — `@RestController`, 응답 DTO 변환, 인증 정보 추출                                          |

## 상황에 따라

| 스킬                                        | 언제                                                                                                               |
|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| [kotlin-style](kotlin-style/SKILL.md)       | 조건·분기·반복의 Kotlin 문법 — sealed 상태, `else` 없는 `when`, null 좁히기, 함수 본문 모양, 코루틴 취소 예외      |
| [kotlin-error](kotlin-error/SKILL.md)       | 실패 분기를 만들 때 — `ApiException` 하위 예외가 status·code 를 스스로 갖는다, `ProblemDetail`, 상태코드·로그 레벨 |
| [kotlin-auth](kotlin-auth/SKILL.md)         | 로그인·권한·"내 것만 조회" — 소유자 스코프, 401·403·404 구분                                                       |
| [kotlin-client](kotlin-client/SKILL.md)     | 외부 API 연동 — 처음 붙일 때 실응답부터 확인하는 법, `clients/client-*`, `internal` 캡슐화, 타임아웃, 쿼터·폴백    |
| [kotlin-config](kotlin-config/SKILL.md)     | 설정값·접속 정보·API 키 — 모듈별 yml, default·`local` 프로파일, 비밀값 주입, 로컬 PostGIS                          |
| [kotlin-logging](kotlin-logging/SKILL.md)   | 로그를 남길 때 — 레벨 기준, `{}` 치환, 넣으면 안 되는 것, 서버의 로그 감시가 읽을 줄                               |
| [kotlin-api-docs](kotlin-api-docs/SKILL.md) | (도입 시) 외부가 쓰는 API 를 REST Docs 로 문서화할 때 — 지금 저장소에는 없다                                       |

## 문서 구조

스킬 18종 모두 같은 모양이다. 마지막 두 절만 봐도 리뷰는 된다.

- **규칙** — 지켜야 할 것

- **적발 신호** — 리뷰에서 잡아낼 패턴과 심각도

- **체크리스트** — 작업을 끝내기 전 확인할 것

## 심각도 — 정의는 여기 한 곳

| 심각도         | 뜻                                                                | 리뷰에서                                            |
|----------------|-------------------------------------------------------------------|-----------------------------------------------------|
| **Critical**   | 계층 붕괴, 데이터·보안 사고, 와이어 계약 파손, 변경이 번지는 구조 | **머지 차단.** 머지 전에 고친다                     |
| **Important**  | 유지보수성·가독성·과설계                                          | 수정 권장 코멘트. 고치지 않으면 PR 에 이유를 남긴다 |
| **Code Smell** | 취향에 가까운 작은 냄새                                           | 참고 코멘트. 그 줄을 고칠 일이 생기면 함께 고친다   |

각 스킬의 적발 신호 표는 이 세 단계만 쓴다.

## 예시 코드

예시는 대개 가상 Todo 도메인이다. 경로·모듈·패키지 이름은 이 저장소에 맞췄다
(`core/core-<도메인>`, `storage/db-<이름>`, `io.soo.bueongi...`). 실코드 인용이 더 정확한 자리는
점차 이 저장소 코드(`core-route`·`core-share`·`storage/db-safety-spot` 등)로 바꾼다.

관련 문서: 외부 서비스 목록 [docs/EXTERNAL_SERVICES.md](../../docs/EXTERNAL_SERVICES.md),
API 계약 [docs/API.md](../../docs/API.md), 운영 배포 [deploy/README.md](../../deploy/README.md).
