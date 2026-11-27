# bueongi backend 위치 공유(보호자) 서버 설계와 구현 노트

귀가 중인 사용자의 실시간 위치를 보호자가 로그인 없이 보는 위치 공유 서버의 설계 배경, 저장 모델, 코드 배치와 운영
정책을 정리한다. 요청과 응답의 상세 형태는 [`API.md`](API.md)의 `/share` 절이 단일 레퍼런스다.

> 상태: MVP 구현 완료(2026-06-19). 2026-11-25 저장소를 Redis 하나로 통일(DB와 인메모리 폴백 제거).<br>
> 출발점: 칸반 태스크 `cdb5314d [Backend/Server] Redis 기반 위치 공유 최소 서버 설계/구현`(label `safe-return-mvp`)

## 1. 배경과 요구

- bueongi는 안심 귀가 앱이다. 위치 공유를 뺀 기존 API(장소 검색, 경로 비교, 주변 안심시설)는 상태를 저장하지 않는다.
- 요구(태스크 cdb5314d): 사용자가 토큰 URL을 외부 메신저로 공유하면 보호자가 로그인 없이 실시간 위치를 5초 폴링으로
  조회하는 최소 서버.
- 시간에 따라 변하는 라이브 위치(상태 있음)라 기존 도메인과 성격이 다르다. 만료(TTL)를 강제할 수 있는 저장소(Redis)가
  맞다.

## 2. 설계 원칙

1. `/share` prefix와 공통 에러 계약 재사용: 보호자가 브라우저로 여는 공개 URL이라 `/api`와 분리한다. 좌표 오류는
   `core-common`의 `ApiControllerAdvice`가 내는 RFC 7807 + `code`(400) 그대로다. 토큰 없음/만료와 `owner_secret`
   불일치는 404 `SHARE_SESSION_NOT_FOUND`로 같게 응답한다. CORS는 `/api/**` 정책을 `/share/**`에도 적용한다.
2. 개인정보 최소화와 비식별 토큰: 계정이나 전화번호 대신 추측할 수 없는 128-bit URL-safe 랜덤 `token`(22자)으로만
   식별한다(링크 공유 모델). 쓰기 요청은 생성 때만 받는 `owner_secret`으로 소유자를 확인한다.
3. 자동 만료(TTL): 상한 TTL 1~3시간이라 공유를 끄지 않아도 시간이 지나면 위치가 사라진다(무기한 추적 금지). 사용자가
   먼저 끝내려면 `POST /share/{token}/end`를 쓴다.
4. Redis 필수: 위치 공유는 Redis에만 저장한다. 연결이 끊기면 위치 공유 API만 503 `SHARE_STORE_UNAVAILABLE`을 내고 서버
   기동과 다른 API는 영향이 없다. 보호자 지도는 지도 SDK 키가 필요 없다(Leaflet과 OpenStreetMap).
5. 좌표 규약 동일: lat -90~90, lng -180~180. 비숫자(`NaN`, `Infinity`)와 범위 밖 값은 거부한다.

## 3. API 계약

사용자 앱의 호출 대상은 dev 기준 `http://localhost:8119`다. 상세 형태는 [`API.md`](API.md)를 본다.

| 메서드 | 경로                      | 호출자    | 요약                                                   |
|--------|---------------------------|-----------|--------------------------------------------------------|
| `POST` | `/share/create`           | 사용자 앱 | 토큰, `owner_secret`, `share_url`, `expires_at` 발급   |
| `POST` | `/share/{token}/location` | 사용자 앱 | 위치(와 남은 거리, 시간) 갱신                          |
| `GET`  | `/share/{token}/location` | 보호자 웹 | 항상 200, 만료는 `expired: true`, 진행 상태는 `status` |
| `POST` | `/share/{token}/route`    | 사용자 앱 | 목적지, 경로, 경로변 시설 올리기                       |
| `GET`  | `/share/{token}/route`    | 보호자 웹 | 항상 200, 경로가 없으면 `route: null`                  |
| `POST` | `/share/{token}/end`      | 사용자 앱 | 공유 종료(없는 토큰도 200)                             |
| `POST` | `/share/{token}/watching` | 사용자 앱 | 보호자가 최근 12초 안에 위치를 조회했는지              |
| `GET`  | `/share/{token}`          | 보호자    | `text/html` 보호자 지도 페이지(Leaflet, OSM, 5초 폴링) |

관리자 화면용 조회(`/api/admin/share-status`, `/api/admin/share-sessions` 등)는 같은 모듈의 `ShareAdminController`에
있다. 관리자 위치 조회는 보호자 시청 기록을 남기지 않는다.

위치 공유가 쓰는 에러 `code`:

| code                      | HTTP | 발생 조건                                                |
|---------------------------|-----:|----------------------------------------------------------|
| `VALIDATION_FAILED`       |  400 | 좌표 범위 위반, 필수 값 누락, `expires_in_hours` 범위 밖 |
| `MALFORMED_REQUEST`       |  400 | 깨진 JSON, `NaN` 같은 비숫자 리터럴                      |
| `SHARE_SESSION_NOT_FOUND` |  404 | 토큰 없음/만료(쓰기 요청), `owner_secret` 누락/불일치    |
| `INVALID_ROUTE_META`      |  422 | 공유 경로 `type`이 `safe`, `main`, `fast` 밖             |
| `SHARE_STORE_UNAVAILABLE` |  503 | Redis 접근 실패(`Retry-After: 5`)                        |

## 4. 데이터 모델(Redis)

```text
share:{token}     HASH   { created_at, expires_at, owner_secret, expected_arrival_at? }  EXPIRE = 상한 TTL(1~3h)
location:{token}  HASH   { lat, lng, updated_at, remaining_meters?, remaining_seconds? }   EXPIRE = 같은 상한 TTL
route:{token}     STRING 경로 JSON(목적지, 경로, 마커, 전체 거리/시간, 유형)                EXPIRE = 같은 상한 TTL
viewer:{token}    STRING "1"                                                                EXPIRE = 12초
```

- 상한 만료는 Redis native TTL이 강제한다. `findByToken`은 `share:{token}` 키가 없으면 만료/미존재로 본다.
- 위치와 경로 키도 공유와 같은 상한 TTL이라 만료 시 함께 사라진다(잔존 추적 금지). 개인정보는 저장하지 않는다.
- `expected_arrival_at`은 생성 요청에 `eta_seconds`가 있을 때만 저장하며 `now + eta_seconds + 60초`다.
- 남은 거리, 시간은 옛 이름(`remaining_m`, `remaining_s`)으로 저장된 값도 읽는다.
- `viewer:{token}`은 보호자가 `GET /share/{token}/location`을 부를 때마다 12초 TTL로 다시 쓴다. `watching` 판정에
  쓴다.
- `POST /share/{token}/end`는 네 키를 함께 지운다.
- 경로 JSON이 깨져 있으면 경로만 생략하고 공유와 위치는 계속 보여준다.

## 5. 코드 배치

```text
core/core-share/src/main/kotlin/io/soo/bueongi/core/share/
├── api/controller/
│   ├── ShareController.kt         # /share 엔드포인트 8개
│   ├── ShareAdminController.kt    # /api/admin/share-* 조회
│   ├── ShareStoreAdvice.kt        # LocationStoreUnavailableException -> 503 SHARE_STORE_UNAVAILABLE
│   └── GuardianPage.kt            # 보호자 HTML에 토큰을 JS 이스케이프해 주입
├── api/request/                   # 요청 DTO, @Valid 좌표 검증, snake_case(@JsonProperty)
├── api/response/                  # 응답 DTO(LocationView, RouteView, WatchResponse 등)
└── domain/
    ├── service/ShareService.kt    # 생성, 위치/경로 저장과 조회, 종료, 소유자 확인, 토큰 발급
    ├── model/SharePolicy.kt       # 기본 만료, 유예, 정지 판정, 도착 반경 상수와 진행 상태 판정
    └── error/                     # SHARE_SESSION_NOT_FOUND, INVALID_ROUTE_META
core/core-share/src/main/resources/share/guardian.html  # 보호자 지도 페이지(Leaflet, OSM, 5초 폴링)
storage/db-share/src/main/kotlin/io/soo/bueongi/storage/db/share/
├── store/ShareSessionStore.kt                 # 저장소 인터페이스
├── store/RedisShareSessionStore.kt            # Redis 구현(위 키 모델)
├── store/ShareStoreConfig.kt                  # Redis 저장소 빈 등록
├── store/LocationStoreUnavailableException.kt # Redis 접근 실패
└── model/                                     # ShareSession, StoredLocation, StoredRoute 등
storage/db-share/src/main/resources/db-share.yml  # spring.data.redis.url = ${BUEONGI_REDIS_URL:redis://localhost:6379}
storage/db-share/src/testFixtures/kotlin/.../store/InMemoryShareSessionStore.kt  # 테스트 대역
```

- Redis 연결은 Spring Boot 자동 설정이 `spring.data.redis.url`(`BUEONGI_REDIS_URL`)로 만든다.
- `SHARE_SESSION_NOT_FOUND`와 `INVALID_ROUTE_META`는 `ApiException` 하위 클래스라 공통 `ApiControllerAdvice`가
  처리한다.
- `/api/health`의 `locationStore`는 `ShareSessionStore.backend` 값이다. 운영 구현은 `"redis"`이고 주소와 자격증명은
  노출하지 않는다.

## 6. 진행 상태 판정

보호자가 `GET /share/{token}/location`을 부를 때마다 `ShareService`가 진행 상태(`status`)를 계산해 응답에 싣는다
(`SharePolicy.kt`의 `progressStatus`). 주기적으로 훑는 감시는 없고, 이 상태로 운영자에게 알리지도 않는다.

- 예정 시각 초과: `expected_arrival_at` 뒤 300초가 지났다. `eta_seconds` 없이 만든 공유는 판정하지 않는다.
- 위치 정지: 마지막 위치 갱신 뒤 90초가 지났거나 위치가 한 번도 없다.
- 둘 다 참이면 마지막 위치가 목적지 반경 30m 안일 때(남은 거리가 보고돼 있으면 그것도 30m 이하) `arrival_likely`,
  아니면 `missed_arrival_suspect`다. 경로를 올리지 않았으면 목적지를 몰라 `missed_arrival_suspect`가 된다.
- 둘 중 하나라도 거짓이면 `in_progress`다.

## 7. 인프라와 설정

| 환경변수                 | 기본값                                         | 설명                                     |
|--------------------------|------------------------------------------------|------------------------------------------|
| `BUEONGI_REDIS_URL`      | `redis://localhost:6379`                       | 위치 공유 저장 Redis                     |
| `BUEONGI_SHARE_BASE_URL` | `http://localhost:8119`                        | `share_url`의 앞부분(운영은 공개 도메인) |
| `BUEONGI_CORS_ORIGINS`   | `https://localhost:3619,http://localhost:3619` | `/api/**`, `/share/**` 공통 허용 origin  |

## 8. 수용 기준

다음 기준을 구현하고 테스트로 검증했다.

1. 생성, 위치 POST, GET 순으로 같은 좌표를 회수한다(Redis는 `StringRedisTemplate` 목으로 키 계약 검증).
2. `expires_in_hours`는 1, 2, 3만 허용하고 미지정 시 1시간이다.
3. 상한 만료(1~3시간) 경과 후 GET은 `expired: true`(좌표 null), 위치 POST는 404다.
4. 잘못된 좌표(범위 밖, 비숫자) POST는 400이다(기존 계약과 같은 code).
5. 응답, 로그, health에 개인정보와 Redis 자격증명이 나가지 않는다.
6. 보호자 HTML이 키 없이(OSM) 렌더되고 토큰이 주입된다.

## 9. Redis 장애 시 동작 정책

- 저장소는 Redis 하나다(2026-11-25 통일). `RedisShareSessionStore`는 Redis 접근 실패(`DataAccessException`)를
  `LocationStoreUnavailableException`으로 바꾸고, `ShareStoreAdvice`가 503 `SHARE_STORE_UNAVAILABLE`과
  `Retry-After: 5`로 응답하고 ERROR 로그(`미처리 예외 | 공유 저장소 | 503 | ...`)를 남긴다. 운영 알림은 앱이 보내지
  않는다. 서버의 로그 감시가 ERROR 로그와 5xx 요청 로그를 보고 알린다.
- 폴백 저장소는 두지 않는다. 폴백하면 재시작이나 다중 인스턴스에서 세션이 조용히 갈라진다.
- 테스트는 `storage:db-share` testFixtures의 `InMemoryShareSessionStore`를 대역으로 쓴다(Redis 없이 전 테스트 통과).

## 10. 범위 밖(v2)

보호자 회원가입, 페어링, 푸시 알림, 서버 발송 메시지, 경로 이탈 자동 감지, WebSocket/SSE 푸시(폴링으로 충분), 위치
정확도와 방향 필드.

## 부록: 초기 설계(18~19회차)와의 차이

MVP 이전 설계(`/api/share/sessions`)에서 현재 계약으로 바뀐 내역이다.

| 항목          | 초기 설계                          | 현재                                                |
|---------------|------------------------------------|-----------------------------------------------------|
| 경로 prefix   | `/api/share/sessions`              | `/share`                                            |
| 생성          | `POST .../sessions` `{ttlSeconds}` | `POST /share/create` `{expires_in_hours: 1\|2\|3}`  |
| 위치 갱신     | `PUT .../{shareId}/location`       | `POST /share/{token}/location`                      |
| 조회          | `GET .../{shareId}`, 200/204/404   | `GET /share/{token}/location`, 항상 200 + `expired` |
| 보호자 페이지 | 없음                               | `GET /share/{token}`(HTML)                          |
| 종료          | `DELETE .../{shareId}`             | `POST /share/{token}/end`(`owner_secret` 필요)      |
| TTL           | 기본 6시간, 상한 12시간(초 단위)   | 1~3시간(기본 1시간)                                 |
| 위치 stale    | 90초 지나면 stale                  | 90초 정지는 진행 상태(`status`) 판정에만 씀         |
| 정확도/방향   | `accuracyMeters`, `headingDegrees` | 없음(`lat`, `lng`와 선택 남은 거리, 시간)           |
| 응답 키 표기  | camelCase                          | snake_case(`share_url`, `expires_at`, `updated_at`) |
| 저장소        | 인메모리만                         | `RedisShareSessionStore`(키 모델은 4절)             |
