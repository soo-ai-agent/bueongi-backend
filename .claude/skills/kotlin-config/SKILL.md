---
paths:
  - "**/src/main/resources/*.yml"
  - "**/config/**"
name: kotlin-config
description: 설정과 비밀값 규칙. 모듈별 yml 파일의 소유와 core-app application.yml 의 spring.config.import, 이 저장소의 프로파일(default + local, application-local.yml), 비밀값을 코드·저장소에 두지 않고 환경변수·.env.local 로 주입하는 법, 로컬 PostGIS(docker compose)와 datasource 설정, @ConfigurationProperties 로 타입 있게 읽는 법을 다룬다. 설정값·접속 정보·외부 주소를 추가할 때 사용한다. "설정 추가", "환경별로 다르게", "API 키", "DB 접속" 요청에도 사용할 것.
---

# 설정과 비밀값

**자리:** 설정은 그 값을 쓰는 **모듈의 `src/main/resources/<모듈>.yml`**, 비밀값은 **저장소 밖**(환경변수, 로컬은 `.env.local`)

## 설정 파일은 모듈이 소유한다

부팅 모듈이 모든 설정을 갖지 않는다. 각 모듈이 자기 설정 파일을 갖고, 부팅 모듈 `core-app` 이 그것을 끌어다 합친다.

```yaml
# core/core-app/src/main/resources/application.yml   (부팅 모듈, 현행 발췌)
spring:
  config:
    import:
      - logging.yml
      - client-slack.yml
      - db-safety-spot.yml
      - db-share.yml
      - client-tmap.yml
```

| 무엇을 설정하나 | 어느 파일 |
|---|---|
| DB 접속·Flyway·(엔티티가 있으면) JPA | `storage/db-<이름>/src/main/resources/db-<이름>.yml` |
| Redis 접속 | `storage/db-share/src/main/resources/db-share.yml` |
| 외부 API 주소 | `clients/client-<이름>/src/main/resources/client-<이름>.yml` |
| 로그 레벨·문자셋 | `support/logging/src/main/resources/logging.yml` |
| 서버 포트·멀티파트·HTTP 타임아웃 등 앱 전역, JWT | 부팅 모듈 `core/core-app/src/main/resources/application.yml` |

**새 모듈을 만들면 그 모듈의 yml 을 만들고 `application.yml` 의 `import` 에 한 줄 추가한다.** 빠뜨리면 설정이 조용히 무시된다.

## 프로파일은 둘 — default 와 local

| 프로파일 | 무엇을 위한 것 | 파일 | DB |
|---|---|---|---|
| (default) | 운영·CI. 모든 값을 환경변수로 받는다 | 각 모듈 yml | 운영 PostgreSQL + PostGIS (`deploy/README.md`) |
| `local` | 개발 PC. `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon` | `core-app/src/main/resources/application-local.yml` | docker compose 의 PostGIS |

`local` 이 하는 일(현행): `.env.local`·`.env.local.properties` 를 `spring.config.import` 로 읽고, 포트를 8119 로 두고,
JWT 키가 없으면 임시 키쌍을 만든다. **local 만을 위한 값은 `application-local.yml` 에 둔다** — 모듈 yml 에 `on-profile: local` 절을 흩뿌리지 않는다.

**값을 추가할 때는 "환경마다 달라지는가"를 먼저 정한다.** 다르면 `${ENV_VAR:기본값}` 자리 하나로 두고 환경변수로 덮는다.
같은 값을 프로파일마다 복사해 두면 하나만 고치고 나머지를 잊는다.

## 로컬 DB — PostGIS 는 docker compose 로

로컬도 운영과 같은 **PostgreSQL 16 + PostGIS** 에 붙는다. H2 같은 메모리 DB 로 대신하지 않는다 — PostGIS 함수와
`geometry` 타입을 돌리지 못한다(**kotlin-migration**).

```yaml
# 로컬 개발용 compose 의 db 서비스 (예시)
services:
  db:
    image: postgis/postgis:16-3.4
    environment:
      POSTGRES_DB: bueongi
      POSTGRES_USER: bueongi
      POSTGRES_PASSWORD: ${BUEONGI_DB_PASSWORD}
    ports:
      - "5432:5432"
```

```yaml
# storage/db-<이름>/src/main/resources/db-<이름>.yml
spring:
  datasource:
    url: ${BUEONGI_DB_URL:jdbc:postgresql://localhost:5432/bueongi?reWriteBatchedInserts=true}
    username: ${BUEONGI_DB_USERNAME:bueongi}
    password: ${BUEONGI_DB_PASSWORD}
```

- 접속 주소·사용자는 로컬 기본값을 줘도 된다. **비밀번호는 기본값 없이** 환경변수(로컬은 `.env.local`)로만 받는다.
- `reWriteBatchedInserts=true` 는 대량 적재의 `batchUpdate` 를 한 문장으로 묶는다(**kotlin-repository**).
- Docker 가 없는 환경(일부 CI·원격 세션)은 로컬에 설치한 PostgreSQL + PostGIS 를 같은 URL 로 띄운다. `context` 테스트도 같은 DB 를 본다(**kotlin-test**).
- Redis 는 지금처럼 `docker run -d -p 6379:6379 redis:7-alpine` 또는 같은 compose 에 둔다.

## 비밀값은 저장소에 없다

**비밀번호·토큰·API 키를 yml 에 값으로 적지 않는다.** 자리만 만들고 값은 밖에서 넣는다.
계정에 묶인 식별자(웹훅 URL 등)도 같다 — 저장소는 공개된다고 보고 쓴다.

```yaml
bueongi:
  jwt:
    public-key: ${JWT_PUBLIC_KEY:}
    private-key: ${JWT_PRIVATE_KEY:}
```

`${...}` 는 환경변수에서 채워진다. 넣는 곳은 셋이다.

| 어디서 | 어떻게 |
|---|---|
| 로컬 셸·`bootRun` | 저장소 루트 `.env.local`(gitignore) — `local` 프로파일이 읽는다 |
| IntelliJ 실행 | 따옴표를 벗긴 `.env.local.properties`(gitignore) — 만드는 법은 README 「IntelliJ에서 실행」 |
| 운영 | 서버의 `deploy/docker-compose.yml` `environment` 절과 `.env` ([deploy/README.md](../../../deploy/README.md)) |

환경변수 이름은 README 「환경변수」 표에 함께 적는다. 새 키를 추가하면 그 표에 한 줄 더한다.

기본값을 주고 싶으면 `${VAR:기본값}` 을 쓰되, **비밀값에는 기본값을 주지 않는다.** 설정이 빠진 채 뜨는 것보다 못 뜨는 것이 낫다.
(외부 API 키처럼 "없으면 그 기능만 폴백"으로 설계된 키는 빈 기본값 `${TMAP_APP_KEY:}` 을 둔다 — 실제 값을 기본값으로 두지 않는 한 같은 규칙이다.)

- 커밋 전에 `git diff` 로 값이 딸려 들어가지 않았는지 본다.

- 실수로 커밋했다면 **값을 지우는 것으로 끝나지 않는다.** 이력에 남으므로 그 자격 증명을 폐기하고 새로 발급한다.

- 로그·예외 메시지·API 응답에도 넣지 않는다 (`kotlin-error`).

## 코드에서 읽을 때 — 타입을 붙인다

`@Value("\${...}")` 를 여기저기 흩뿌리지 않는다. 관련된 값은 한 클래스로 묶어 `@ConfigurationProperties` 로 받는다.

```kotlin
@ConfigurationProperties(prefix = "example.api")
data class ExampleApiProperties(
    val url: String,
    val timeoutMillis: Int = 3000,
)
```

- **타입이 붙어 오타와 형변환 오류를 뜰 때 잡는다.** 문자열로 흩어 두면 실행 중에 터진다.

- 값이 없으면 못 뜨게 하려면 기본값을 주지 않는다.

- 설정을 읽는 클래스는 그 설정을 쓰는 모듈에 둔다. 다른 모듈의 설정을 직접 읽지 않는다. clients 모듈이면 `internal` 로 둔다(**kotlin-client**).

- 비동기 리스너용 executor 처럼 앱 전역 설정은 `core-common/.../common/config/` 에 둔다(`SchedulingConfig` 와 같은 자리).

## 설정으로 뺄 것과 코드에 둘 것

기준은 하나다 — **환경(로컬·운영)마다 달라지는 값인가.**

| | 어디에 | 예 |
|---|---|---|
| 환경마다 다르다 | yml + `@ConfigurationProperties`/`@Value` | 포트, 키, URL, 임계값, 서비스 이름 |
| 계약·프로토콜 값이라 달라지면 안 된다 | 코드 상수 (또는 닫힌 집합이면 enum) | 응답 상태 문자열, 와이어 값, 에러 code |

- **yml 에 이미 있는 값을 코드 상수로 복제하지 않는다.** `spring.application.name` 이 있는데
  `const val SERVICE_NAME = "..."` 을 또 두면 한쪽만 바뀌어 어긋난다 — 주입받아 쓴다.
- 반대로 **와이어 값을 설정으로 빼지 않는다.** 바꾸면 안 되는 값을 바꿀 수 있게 만드는 것이고,
  환경마다 응답 모양이 달라질 길을 연다.
- 설정 네임스페이스(`bueongi.facility.*`·`BUEONGI_FACILITY_*`)는 `.env`·배포 설정과 묶여 있다. 이름을 바꿀 때는 그쪽과 한 번에 바꾼다 —
  일부만 바꾸면 값이 조용히 비어 기능이 0건이 된다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| yml·코드에 비밀번호·토큰·키의 실제 값 | 저장소 이력에 영구히 남는 유출 | Critical |
| 비밀값에 실제 값을 기본값으로 부여 (`${DB_PASSWORD:secret}`) | 설정 누락을 모른 채 잘못된 값으로 기동 | Critical |
| 로컬·테스트 DB 를 H2 등 메모리 DB 로 대체 | PostGIS 쿼리가 운영에서야 터진다 | Critical |
| 새 모듈 yml 을 만들고 `import` 에 추가 안 함 | 설정이 조용히 무시됨 | Important |
| 모듈 yml 에 `on-profile: local` 절을 흩뿌림 | local 값이 여러 파일로 흩어짐 — `application-local.yml` 로 | Important |
| 같은 값을 여러 곳에 복사 | 하나만 고치고 나머지를 잊음 | Important |
| `@Value` 를 여러 클래스에 흩뿌림 | 타입 없음, 어디서 쓰는지 추적 불가 | Important |
| 다른 모듈의 설정 키를 직접 읽음 | 모듈 경계 침범 | Important |
| 새 환경변수를 README 「환경변수」 표에 안 적음 | 운영자가 모른다 | Important |

## 체크리스트

- [ ] 설정을 그 값을 쓰는 모듈의 yml 에 뒀는가

- [ ] 새 yml 이면 `core-app` `application.yml` 의 `import` 에 추가했는가

- [ ] 환경마다 다른 값은 `${ENV:기본값}` 자리 하나로 두고, local 전용 값은 `application-local.yml` 에 뒀는가

- [ ] 비밀값이 `${...}` 자리만 있고 실제 값이 없는가 (기본값도 없는가)

- [ ] 로컬 PostGIS(compose)에 대고 앱이 뜨는가

- [ ] 코드에서 읽는다면 `@ConfigurationProperties` 로 타입을 붙였는가

- [ ] 새 환경변수를 README 「환경변수」 표와 `deploy/docker-compose.yml` 에 더했는가
