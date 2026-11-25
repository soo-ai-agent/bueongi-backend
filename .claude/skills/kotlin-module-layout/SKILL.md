---
paths:
  - "**/*.kt"
  - "**/build.gradle.kts"
  - "settings.gradle.kts"
name: kotlin-module-layout
description: 멀티모듈 배치와 레이어 의존 방향 규칙. 새 파일을 어느 모듈·패키지에 둘지, 여러 도메인이 얽힌 새 기능이 어느 도메인 것인지(주어·타입 생산자·import 역전파), 새 도메인을 만들 기준, 어떤 레이어가 무엇을 참조할 수 있는지 정한다. 새 기능이나 도메인을 추가할 때, "이 클래스를 어디에 두나"·"이건 어느 도메인 일인가"를 판단할 때, 레이어 위반을 리뷰할 때 사용한다.
---

# 모듈 배치와 의존 방향

## 왜 한 덩어리로 안 만드나

이 프로젝트는 폴더가 아니라 **Gradle 모듈**로 나뉘어 있다. 모듈은 폴더와 달리 **서로 참조할 수 있는 방향이 빌드 설정으로 강제된다.**

`storage:db-safety-spot` 은 `core:core-route` 를 참조할 수 없다. 실수로 참조하면 컴파일이 안 된다. 규칙을 사람이 기억하는 대신 빌드가 지켜 주는 것이다.

모듈 목록은 `settings.gradle.kts` 가 정본이다. 2026-11 기준:

```
core/     core-app(부팅) · core-route · core-share · core-place · core-safety-spot
          core-admin-auth · core-admin-ops · core-admin-safety-spot · core-common · core-enum
storage/  db-safety-spot · db-share
clients/  client-place(계약) · client-tmap-place · client-tmap · client-slack · client-safemap
          client-safe182 · client-datagokr-security-lamp · client-safetydata-street-lamp
support/  logging · monitoring · util
```

## 저장 타입과 도메인 모델이 둘인 이유

같은 "할 일" 하나를 두 가지 타입으로 표현한다. 헷갈리기 쉬우니 먼저 구분한다.

| | 무엇인가 | 어디 사나 |
|---|---|---|
| `TodoEntity` 또는 저장 모델(`SafetySpot` 류 불변 data class) | **DB 테이블의 한 행.** JPA 엔티티라면 애너테이션이 붙고 영속성 문맥에 묶인다. JdbcTemplate·Redis 를 쓰는 storage 는 불변 저장 모델을 돌려준다 | `storage` 모듈 소유 |
| `Todo` | **업무 개념.** 프레임워크를 모르는 순수한 데이터 | `core:core-todo` 의 도메인·서비스·컨트롤러 |

storage 의 **불변 저장 모델**은 Store 인터페이스의 반환 타입이라 core 가 읽을 수 있다(**kotlin-repository** 「Store 인터페이스」).
JPA **엔티티**는 변경 감지 문맥에 묶여 있어 구현 레이어 위로 올리지 않는다.

나누는 이유는 **DB 사정이 업무 코드로 번지지 않게** 하기 위해서다. 컬럼을 쪼개거나 테이블을 합쳐도 도메인 모델이 그대로면 위쪽 코드는 아무것도 모른다.

둘 사이의 변환은 **구현 레이어 한 곳에서만** 한다 (`kotlin-implement`).

## 모듈

| 모듈 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `core:core-app` | **유일한 부팅 모듈.** 부팅 클래스, `application.yml`·`application-local.yml`, `/api/health`·`/api/app-info`, 웹 테스트(`@WebMvcTest`·`@SpringBootTest`) | 특정 도메인의 Controller·Service |
| `core:core-<도메인>` | 한 도메인을 통째로 — 그 도메인의 Controller·DTO·Service·구현체·도메인 모델 | 다른 도메인의 코드, 저장 구현, 외부 호출 |
| `core:core-common` | **둘 이상의 도메인이** 쓰는 것만 — `ApiException`·`ApiControllerAdvice`, 사건 타입, 공용 요청 DTO, 전역 웹 설정(CORS·필터) | 한 도메인만 쓰는 것 |
| `core:core-enum` | **여러 모듈이** 공유하는 enum | 한 모듈만 쓰는 enum, 값·로직 |
| `storage:db-<이름>` | 저장·조회. Store 인터페이스와 구현, 엔티티·리포지토리, 저장 모델, 그 테이블의 Flyway 마이그레이션 | 비즈니스 흐름, HTTP, `core-common` 의존 |
| `clients:client-*` | 외부 시스템 호출 어댑터 | 비즈니스 판단, 저장소 접근, `core-common` 의존 |
| `support:*` | 로깅·모니터링(알림 인터페이스)·도메인 지식 없는 범용 함수 | 도메인 코드 일체 |

### 도메인 모듈 안은 두 겹이다

```
core/core-<도메인>/…/core/<도메인>/
  api/     controller/ · request/ · response/
  domain/  service/ · model/ · error/ · (config/)
```

`api/` 패키지 → `domain/` 패키지 한 방향이다. `domain/` 이 `api/` 의 DTO 를 알면 위반이다.
응답 DTO 로의 변환은 **Controller 가** 하고, 변환 함수는 그 DTO 의 `companion` 이 갖는다.

> 저장소 README 1조는 아직 "응답 DTO 는 서비스가 `XxxResponse.from(...)` 으로 만든다"고 적혀 있고, 기존 코드 일부
> (예: `SafetySpotCsvFileService.list()` 가 `List<SafetySpotCsvFileResponse>` 를 돌려줌)가 그 모양이다.
> **새 코드는 이 스킬(Controller 변환)을 따르고**, README 1조는 이 방향으로 고칠 예정이다. 기존 코드는 그 파일을 고칠 일이 생길 때 옮긴다.

### 새 기능은 어느 도메인 것인가 — 소유 판정 네 단계

"이 클래스를 어디에 두나"에서 가장 자주 틀리는 것은 **여러 도메인이 관련된 기능**이다. 순서대로 따진다.

**1. 요구사항을 한 문장으로 쓰고 주어를 찾는다.**

"경로의 안전 점수를 매긴다" 의 주어는 경로다 → 경로 도메인이 갖는다. 시설 정보를 읽어 쓰더라도, **읽는 쪽이 아니라 그 일의 주인이 소유자다.**

목적어가 주어로 바뀌는지 본다: "관리자가 사용자를 차단한다" → `user.block(...)` — 차단은 사용자 도메인의 일이다.

**2. 그 타입을 생산하는 쪽이 소유자다.**

두 도메인이 같은 타입을 쓴다고 해서 공용으로 올릴 일이 아니다. **만들어 내는 쪽이 갖고, 쓰는 쪽이 참조한다.**

"공유 타입 때문에 나눌 수 없다"고 판단하기 전에 이것부터 본다 — 타입을 생산자와 함께 옮기면 방향이 단방향으로 풀리는 경우가 많다.

**3. import 역전파로 검산한다.**

엔드포인트(또는 화면)를 뿌리로 두고 그 파일을 **누가 import 하는지** 센다.

| 세어 본 결과 | 자리 |
|---|---|
| 한 도메인만 쓴다 | 그 도메인 안 |
| 둘 이상이 쓴다 | 공용 모듈(`core-common`·`core-enum`)로 올린다 |
| 이름이 도메인을 말하는데 남도 쓴다 | **소유 도메인에 남긴다.** 쓰는 쪽이 그 도메인을 참조한다 |

**4. 공용으로 올리기 전에 한 번 더 묻는다.**

판정은 하나다 — **"다른 프로젝트에 그대로 옮겨도 말이 되는가."** 그렇다면 `support:util` 이고,
인자에 우리 도메인 타입이 있으면 util 이 아니다(**kotlin-common** 「util 로 내릴 것과 내리지 말 것」).

### 새 도메인을 만들 기준

기존 도메인의 패키지로 두지 않고 `core:core-<이름>` 을 새로 만들 값이 있으려면 셋을 다 만족해야 한다.

1. **자기 엔드포인트를 갖는다** — 바깥이 그 도메인을 직접 부른다.

2. **다른 도메인과 서로 참조가 0이다** — 순환이 있으면 나누기 전에 참조부터 줄인다.

3. **이름이 업무 어휘다** — `manager`·`common`·`util` 같은 이름이 나오면 도메인이 아니라 잡동사니다.

셋 중 하나라도 아니면 기존 도메인 안의 패키지로 둔다. 나중에 커지면 그때 떼는 편이 싸다.

### 모듈로 나눌지, 패키지로 둘지

역참조가 0인 것만으로는 부족하다. **바깥(부팅 모듈·다른 도메인)이 그 모듈을 import 하는지 센다.**

| 재는 것 | 나눌 값이 있는 신호 |
|---|---|
| 역참조 수 | 0 (필요조건일 뿐) |
| **바깥의 import 수** | **1 이상.** 0이면 패키지로 충분하다 |

Spring 은 컴포넌트 스캔으로 빈을 찾으므로, 도메인 안쪽 협력자를 모듈로 떼어도
바깥은 그 이름을 부를 일이 없다. 그러면 경계가 파는 것은 내부 방향 강제뿐이고,
그건 의존 방향 테스트로 더 싸게 산다. 모듈을 접으면 그 자리에 방향 테스트를 남긴다.

### 나누기 전에 반드시 재는 것

```bash
# 1) 도메인 간 참조 — 순환이 있으면 나누지 않는다. 리팩토링으로 참조를 줄인 뒤가 시작점이다
grep -rho "<패키지 루트>\.core\.[a-z]*" <각 도메인 폴더> --include='*.kt' | sort -u

# 2) 웹 테스트 수 — @WebMvcTest·@SpringBootTest 는 부팅 모듈에서만 뜬다. 이만큼이 부팅 모듈로 모인다
grep -rl "@WebMvcTest\|@SpringBootTest" --include='*.kt' src/test | wc -l

# 3) 모듈을 넘나드는 테스트 헬퍼 — 복사하지 말고 java-test-fixtures 로 정식 노출한다
```

공용 코드가 갈 곳이 없으면 모듈을 새로 만들기 전에 **이미 있는 공용 모듈의 이름을 넓힌다**
(`core-error` → `core-common` 처럼).

### 나눈 뒤 반드시 확인하는 것

**`BUILD SUCCESSFUL` 은 아무것도 증명하지 않는다.** 셋 다 한다.

1. **모듈별 테스트 실계수의 합이 이전과 같은가.** 파일이 누락되면 조용히 줄어든다.
   삭제한 모듈의 낡은 `build/test-results` 가 총계에 섞이므로 `git clean -xfd <모듈>` 로 먼저 치운다.
2. **`bootJar` 후 실제로 띄운다.** 컴파일은 되는데 빈 배선이 깨져 못 뜨는 경우가 있다
   (컨트롤러가 다른 모듈로 가면 `@ComponentScan` 범위·`@Conditional` 이 함께 흔들린다).
3. **주요 엔드포인트를 실제로 호출한다.** 정상 1건 + 에러 계약 1건이면 충분하다.
   상시 개발 서버가 있다면 그 포트를 쓰지 말고 다른 포트로 띄운다.

### 모듈을 접을 때 — 방향 테스트를 남긴다

바깥의 import 가 0이라 모듈을 패키지로 접으면, 빌드가 강제하던 내부 방향 규칙이 사라진다.
어겨도 컴파일은 되므로 **소스 파일의 `import` 줄을 읽어 금지 패키지를 찾는 테스트**를 그 자리에 남긴다.

짝으로 "검사 대상 파일이 실제로 존재하는지" 확인하는 테스트를 함께 둔다 — 경로가 바뀌면
검사가 빈 목록을 훑으며 조용히 통과하기 때문이다.

### 이동 함정 — 리소스는 따라가지 않는다

`git mv` 로 `.kt` 만 옮기고 모듈 폴더를 지우면 **`src/main/resources` 가 함께 사라진다.**
`application.yml`·정적 파일이 여기서 유실되기 쉽다. 삭제 전에 목록을 뽑아 두고,
지운 뒤 `git show HEAD:<경로>` 로 되살려 새 모듈에 넣는다.

## 템플릿과 다른 점 — 여기가 성장형이다

레이어 이름과 패키지 규약은 `team-dodn/spring-boot-kotlin-template` 을 따르지만(README 「프로젝트 구조」), 템플릿의
`core/domain/` 평면 구조는 따르지 않는다. 템플릿 README 가 직접 밝히고 있다 — "This is not the best structure...
**your structure must grow too**". 도메인 모듈과 구현 레이어가 그 성장형이고, 이 문서의 배치표가 이 저장소의 기준이다.

## 새 도메인 하나를 추가할 때의 파일 자리

```
core/core-enum/…/io/soo/bueongi/core/enums/TodoStatus.kt                 enum (여러 모듈이 쓸 때만)
storage/db-todo/src/main/resources/db/migration/V…__create_todo.sql      마이그레이션
storage/db-todo/…/io/soo/bueongi/storage/db/todo/TodoStore.kt            Store 인터페이스 또는 리포지토리
storage/db-todo/…/io/soo/bueongi/storage/db/todo/TodoRecord.kt           저장 모델 (JPA 면 TodoEntity)
storage/db-todo/src/main/resources/db-todo.yml                           모듈 설정
core/core-todo/…/io/soo/bueongi/core/todo/domain/model/Todo.kt           도메인 모델
core/core-todo/…/io/soo/bueongi/core/todo/domain/service/TodoFinder.kt   구현 레이어
core/core-todo/…/io/soo/bueongi/core/todo/domain/service/TodoAppender.kt
core/core-todo/…/io/soo/bueongi/core/todo/domain/service/TodoService.kt  도메인 서비스
core/core-todo/…/io/soo/bueongi/core/todo/api/request/TodoCreateRequest.kt
core/core-todo/…/io/soo/bueongi/core/todo/api/response/TodoResponse.kt
core/core-todo/…/io/soo/bueongi/core/todo/api/controller/TodoController.kt
core/core-app/src/test/…/TodoControllerTest.kt                           웹 테스트(부팅 모듈)
```

새 모듈이면 `settings.gradle.kts` 의 `include` 와 `core-app/build.gradle.kts` 의 `implementation`, `application.yml` 의
`spring.config.import` 세 곳에 한 줄씩 더한다(**kotlin-config**).

**만드는 순서는 위에서 아래로.** 아래에서 위로 쌓아야 상위 레이어가 이미 있는 것을 조립하게 된다.

## 의존 방향 — 상위에서 하위로만

```
Controller  →  Domain Service  →  Implement  →  Repository / Client
                     ↓
                Domain Model
```

- 역방향 참조 금지. 하위 레이어가 상위를 알면 안 된다.

- 레이어 건너뛰기 금지: 컨트롤러가 리포지토리를, 도메인 서비스가 리포지토리를 직접 부르지 않는다.

- **엔티티는 구현 레이어 위로 올라가지 않는다.** 구현 레이어가 지역변수로 다루고
  도메인 모델로 바꿔 올린다. 변환은 구현 레이어 소유의 `internal` 확장 함수다(`kotlin-implement`).
  Store 가 돌려주는 불변 저장 모델도 같은 자리에서 도메인 모델로 바꾸는 것이 기본이다 — 도메인 모델과 모양이 같아
  바꿀 것이 없을 때만 그대로 쓴다(지금 `SafetySpot` 류가 그렇다).

- **변환을 storage 에 둘 수는 없다.** 변환 결과(도메인 모델)가 core 소유라, storage 가
  core 를 import 하게 되어 방향이 뒤집힌다. 타입을 생산하는 쪽이 소유자다.

- **storage 가 올려다볼 수 있는 것은 `core:core-enum` 하나뿐이다** — 저장 모델·엔티티의 enum 값이
  공유 enum 일 때다(`storage/db-safety-spot/build.gradle.kts` 의 `SafetySpotType`). `core-common` 은 금지다: 저장 모델·엔티티는
  `ApiException` 을 던질 수 없고, 불변식 위반은 표준 예외(`check`·`require`)로 던진다.
  도메인 판정(404·409)은 core 가 그것을 받아 자기 예외로 바꾼다.

- 컨트롤러의 요청/응답 DTO 는 도메인 아래로 내려가지 않는다.

- 도메인 모델은 JPA·Spring Web 애너테이션을 갖지 않는다. 프레임워크 의존은 엔티티·컨트롤러·어댑터에만 둔다.

## 도메인 경계

- **도메인 모듈끼리는 서로 의존하지 않는다**(README 「프로젝트 구조」). `core-route` 의 `build.gradle.kts` 에
  `project(":core:core-share")` 가 보이면 위반이다.

- 다른 도메인의 데이터가 필요하면 둘 중 하나로 푼다 —
  ① 그 데이터를 가진 **storage 의 Store 인터페이스**를 직접 의존한다(`core-route` → `storage:db-safety-spot`),
  ② 답을 기다리지 않는 통지라면 **`core-common` 의 사건 타입**으로 이벤트를 발행한다(**kotlin-domain-service**).

- 애그리게이트 경계를 넘는 상태 변경은 루트 엔티티를 통해 수행한다 (kotlin-entity 참고).

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 컨트롤러가 리포지토리·엔티티를 직접 참조 | 레이어 건너뛰기 | Critical |
| 엔티티가 도메인 모듈에 노출됨(반환·파라미터) | 저장소 격리 붕괴 | Critical |
| 도메인 서비스가 리포지토리를 직접 호출 | 구현 레이어 우회 | Critical |
| 하위 레이어가 상위 타입을 import | 역방향 의존 | Critical |
| 도메인 모듈이 다른 도메인 모듈을 의존 (`project(":core:core-<다른 도메인>")`) | 도메인 경계 침범 | Critical |
| storage·clients 가 `core-common` 을 의존 | 하위가 상위를 안다 | Critical |
| core·storage·support 모듈 안의 외부 호출(RestClient·SDK) | 외부 연동은 clients 모듈 전용 | Critical |
| 도메인 모델에 JPA·Web 애너테이션 | 프레임워크 침투 | Important |
| 쓰는 쪽이 소유한 계산·타입 (주어가 다른 도메인인데 여기 있음) | 소유 판정 위반 — 주어에게 돌려준다 | Important |
| 한 도메인만 쓰는데 공용 모듈에 올라간 타입 | 공용이 잡동사니가 된다 | Important |
| 도메인 모듈 안에서 `api/`·`domain/` 구분 없이 파일 누적 | 경계 흐려짐 | Important |
| 웹 테스트(`@WebMvcTest`·`@SpringBootTest`)가 `core-app` 밖에 있음 | 부팅 모듈 밖에서는 컨텍스트가 뜨지 않는다 | Important |

## 체크리스트

- [ ] 새 파일이 위 배치표의 자리에 있는가

- [ ] import 방향이 상위 → 하위 단방향인가

- [ ] 엔티티가 구현 레이어 위(서비스 시그니처·도메인 모델·컨트롤러)로 새지 않았는가

- [ ] 다른 도메인 모듈을 의존하지 않고, storage 인터페이스나 `core-common` 사건으로만 닿는가

- [ ] 새 기능의 소유 도메인을 주어·생산자·import 역전파로 판정했는가

- [ ] 공용 모듈에 올린 것이 실제로 둘 이상의 도메인에서 쓰이는가
