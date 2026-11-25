# bueongi backend API 계약

안심 귀가 앱 백엔드의 API 명세다. 앱과 관리자 화면이 연동할 때 참조한다. 이 문서는 코드(컨트롤러, 요청/응답 DTO, 서비스,
상수)에서 직접 파생했고 각 절에 출처 파일을 적는다. 계약을 바꾸면 코드와 이 문서를 함께 갱신한다.

> 범위: 현재 계약의 스냅샷<br>
> 위치 공유 설계 배경: [`LOCATION_SHARING_DESIGN.md`](LOCATION_SHARING_DESIGN.md)

## 개요

- Base URL(dev): `http://localhost:8119`
- 포트: 기본 `application.yml`의 `server.port`는 `8080`이고, `local` 프로파일(`application-local.yml`)이 `8119`로 둔다.
  `SERVER_PORT`를 주면 그 값이 우선한다.
- CORS: `/api/**`와 `/share/**`는 `BUEONGI_CORS_ORIGINS`(쉼표 구분, 기본 `https://localhost:3619,http://localhost:3619`)만
  허용하고, 메서드는 `GET`, `POST`, `OPTIONS`만 허용한다. `/cdn/**`는 모든 origin의 `GET`을 허용한다. 출처:
  `core-common/.../config/WebCorsConfig.kt`.
- 위치 공유(`/share`)를 뺀 앱 API는 상태를 저장하지 않는다. 경로 응답은 routeId가 아니라 좌표 기반이라 새로고침이나
  직접 진입에도 끊기지 않는다.
- 외부 API에 의존하는 엔드포인트(장소 검색, 경로)는 외부 호출이 실패하면 근사 결과를 지어내지 않고 503으로 알린다.
- 좌표 규약: 위도 `lat` -90~90, 경도 `lng` -180~180.

## 엔드포인트 목록

`/api/admin/**`는 `SecurityConfig`가 `ROLE_ADMIN`으로 보호한다(Bearer JWT, access token). 그 밖의 경로는 인증 없이
호출한다.

| 메서드 | 경로                                                 | 인증   | 설명                              |
|--------|------------------------------------------------------|--------|-----------------------------------|
| `GET`  | `/api/health`                                        | 없음   | 기동 확인과 위치 공유 저장소 종류 |
| `GET`  | `/api/app-info`                                      | 없음   | 서버 버전과 앱 업데이트 기준      |
| `GET`  | `/api/places/search`                                 | 없음   | 장소 검색                         |
| `POST` | `/api/routes`                                        | 없음   | 경로 옵션 목록(safe, main, fast)  |
| `GET`  | `/api/safety-spots/nearby`                           | 없음   | 주변 안심시설 집계와 마커         |
| `POST` | `/share/create`                                      | 없음   | 위치 공유 생성                    |
| `POST` | `/share/{token}/location`                            | 소유자 | 위치 갱신                         |
| `GET`  | `/share/{token}/location`                            | 없음   | 보호자 위치 조회                  |
| `POST` | `/share/{token}/route`                               | 소유자 | 공유 경로 올리기                  |
| `GET`  | `/share/{token}/route`                               | 없음   | 보호자 경로 조회                  |
| `POST` | `/share/{token}/end`                                 | 소유자 | 공유 종료                         |
| `POST` | `/share/{token}/watching`                            | 소유자 | 보호자 시청 여부                  |
| `GET`  | `/share/{token}`                                     | 없음   | 보호자 지도 페이지(HTML)          |
| `POST` | `/api/v1/auth/admin/login`                           | 없음   | 관리자 로그인                     |
| `POST` | `/api/v1/auth/local/token/refresh`                   | 없음   | 관리자 토큰 갱신                  |
| `GET`  | `/api/admin/me`                                      | 관리자 | 로그인한 관리자 정보              |
| `GET`  | `/api/admin/share-status`                            | 관리자 | 활성 공유 수                      |
| `GET`  | `/api/admin/share-sessions`                          | 관리자 | 활성 공유 목록                    |
| `GET`  | `/api/admin/share-sessions/{token}/location`         | 관리자 | 공유 위치 조회(시청 기록 없음)    |
| `GET`  | `/api/admin/share-sessions/{token}/route`            | 관리자 | 공유 경로 조회                    |
| `GET`  | `/api/admin/alerts`                                  | 관리자 | 장애 알림 이력                    |
| `GET`  | `/api/admin/slack-config`                            | 관리자 | Slack 알림 설정 조회              |
| `PUT`  | `/api/admin/slack-config`                            | 관리자 | Slack 알림 설정 편집              |
| `POST` | `/api/admin/slack-config/test`                       | 관리자 | Slack 테스트 발송                 |
| `GET`  | `/api/admin/safety-spot-fetches`                     | 관리자 | 시설 외부 수집 현황               |
| `GET`  | `/api/admin/safety-spot-status`                      | 관리자 | 시설 적재 현황                    |
| `GET`  | `/api/admin/safety-spot-status/regions`              | 관리자 | 지역별 시설 현황                  |
| `GET`  | `/api/admin/safety-spot-status/regions/{code}/spots` | 관리자 | 지역 시설 목록                    |
| `GET`  | `/api/admin/safety-spot-csv-files`                   | 관리자 | 시설 CSV 파일 목록                |
| `GET`  | `/api/admin/safety-spot-csv-files/rows`              | 관리자 | 시설 CSV 행 쪽 조회               |
| `POST` | `/api/admin/safety-spots/{type}/upload`              | 관리자 | 시설 CSV 업로드(multipart)        |

인증 열의 "소유자"는 요청 본문의 `owner_secret`으로 공유 소유자임을 증명한다는 뜻이다.

## 공통 에러 스키마

검증, 파싱, 도메인, 프레임워크 실패는 모두 RFC 7807 ProblemDetail에 `code`를 더한 아래 스키마로 응답한다. 출처:
`core-common/.../error/ApiControllerAdvice.kt`, `ApiException.kt`. 관리자 인증 실패는 필터가 같은 본문을 직접 쓰고
(`core-admin-auth/.../config/AuthProblemResponse.kt`), 위치 공유 저장소 장애는 `core-share`의 `ShareStoreAdvice`가
처리한다.

- `Content-Type: application/problem+json`
- Body:

```json
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "요청 본문 검증에 실패했습니다.",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "origin.lat", "message": "...", "rejectedValue": 999.0 }
  ]
}
```

- `code`: 기계가 읽는 분기 키. 모든 에러 응답에 있으므로 프론트는 `status` 대신 `code`로 분기한다.
- `errors[]`: 필드 단위 사유이며 일부 code에만 있다. `field`는 중첩 시 `origin.lat`, `destination.lng` 형태다.
  `rejectedValue`는 거부된 값(또는 길이, 거리 같은 파생값)이다.

### 에러 code 전체 표

| code                             | HTTP | title                       | errors                  | 발생 조건                                                     |
|----------------------------------|-----:|-----------------------------|-------------------------|---------------------------------------------------------------|
| `VALIDATION_FAILED`              |  400 | Validation Failed           | 있음                    | 좌표 범위 위반, 필수 필드/파라미터 누락, 형식 오류            |
| `MALFORMED_REQUEST`              |  400 | Malformed Request           | 없음                    | 빈 본문, 깨진 JSON, `NaN`/`Infinity` 같은 비숫자 리터럴       |
| `INVALID_KEYWORD`                |  400 | Invalid Keyword             | `keyword`(입력 길이)    | 검색어가 트림 후 100자 초과                                   |
| `INVALID_CSV_PAGE`               |  400 | Bad Request                 | `page`, `size`          | CSV 쪽 조회의 `page < 0` 또는 `size`가 1~200 밖               |
| `INVALID_SPOT_LIMIT`             |  400 | Bad Request                 | `limit`                 | 지역 시설 목록의 `limit < 1`                                  |
| `UNKNOWN_SAFETY_SPOT_TYPE`       |  400 | Unknown SafetySpot Type     | `type`                  | 업로드 경로의 `{type}`이 알려진 시설 종류가 아님              |
| `UNAUTHORIZED`                   |  401 | Unauthorized                | 없음                    | 관리자 토큰 없음/만료/위조, 로그인 실패, 잘못된 refresh token |
| `FORBIDDEN`                      |  403 | Forbidden                   | 없음                    | 관리자 권한이 없는 토큰                                       |
| `SHARE_SESSION_NOT_FOUND`        |  404 | Share Session Not Found     | 없음                    | 공유 토큰 없음/만료 또는 `owner_secret` 누락/불일치           |
| `SAFETY_SPOT_CSV_FILE_NOT_FOUND` |  404 | Not Found                   | 없음                    | CSV 쪽 조회의 파일이 없음                                     |
| `NOT_FOUND`                      |  404 | Not Found                   | 없음                    | 매핑된 핸들러나 정적 리소스가 없는 경로                       |
| `METHOD_NOT_ALLOWED`             |  405 | Method Not Allowed          | 없음                    | 지원하지 않는 메서드. `Allow` 헤더를 보존한다                 |
| `UNSUPPORTED_MEDIA_TYPE`         |  415 | Unsupported Media Type      | 없음                    | JSON 본문 엔드포인트에 비 JSON `Content-Type`                 |
| `SAME_ORIGIN_DESTINATION`        |  422 | Same Origin And Destination | `destination`(직선거리) | 출발-도착 직선거리 1m 미만                                    |
| `TRIP_TOO_FAR`                   |  422 | Trip Too Far                | `destination`(직선거리) | 출발-도착 직선거리 100km 초과                                 |
| `INVALID_ROUTE_META`             |  422 | Invalid Route Metadata      | `type`                  | 공유 경로의 `type`이 `safe`, `main`, `fast` 밖                |
| `INVALID_SLACK_CATEGORY`         |  422 | Invalid Slack Category      | `mutedCategories`       | Slack 설정의 뮤트 목록에 알 수 없는 카테고리 key              |
| `INTERNAL_ERROR`                 |  500 | Internal Server Error       | 없음                    | 미처리 예외. 응답에 스택트레이스나 예외 메시지를 싣지 않는다  |
| `PLACE_SEARCH_UNAVAILABLE`       |  503 | Place Search Unavailable    | 없음                    | 외부 장소 검색(Tmap POI) 호출 실패                            |
| `ROUTE_SEARCH_UNAVAILABLE`       |  503 | Route Search Unavailable    | 없음                    | Tmap 경로 후보를 하나도 얻지 못함                             |
| `SHARE_STORE_UNAVAILABLE`        |  503 | Service Unavailable         | 없음                    | 위치 공유 저장소(Redis) 접근 실패. `Retry-After: 5`           |

## `GET /api/health`: 운영 readiness

서비스 가동 여부와 위치 공유 저장소 종류를 알려준다. 출처: `core-app/.../controller/HealthController.kt`,
`HealthService.kt`.

요청: 없음.

200 응답(`HealthStatus`):

```json
{ "status": "UP", "service": "bueongi-backend", "locationStore": "redis" }
```

- `status`: 항상 `"UP"`(요청을 처리할 수 있으면 도달).
- `service`: `spring.application.name`, 곧 `"bueongi-backend"`.
- `locationStore`: 운영에서는 `"redis"` 고정이다. 연결 상태가 아니라 저장소 종류 라벨이며 URL과 자격증명은 노출하지
  않는다.

## `GET /api/app-info`: 스플래시용 서버 버전

모바일 시작 화면이 한 번 호출로 서버 버전을 얻는다. 응답이 오면 서버가 살아 있다는 뜻이기도 하다. 출처:
`core-app/.../controller/AppInfoController.kt`, `AppInfoService.kt`.

요청: 없음.

200 응답(`AppInfoResponse`):

```json
{
  "serverVersion": "0.0.1-SNAPSHOT",
  "minSupportedAppVersion": "1.1.0",
  "latestAppVersion": "1.4.2"
}
```

- `serverVersion`: 빌드 정보(`build-info.properties`, Gradle `springBoot { buildInfo() }`가 만든다)에서 온다. 빌드
  정보가 없으면 서버가 뜨지 않는다. 빈 버전이 몰래 나가지 않게 하려는 것이다. IntelliJ로 실행할 때는 Gradle로
  빌드하도록(기본값) 둔다.
- `minSupportedAppVersion`: 이 버전 미만의 앱은 강제 업데이트 화면을 띄운다. 설정
  `bueongi.app.min-supported-version`(환경변수 `BUEONGI_APP_MIN_SUPPORTED_VERSION`)에서 오고, 빈 값이면 정책이
  없어 앱이 판정을 건너뛴다.
- `latestAppVersion`: 이 버전 미만의 앱에 "새 버전이 있어요" 권장 알림을 한 번 띄운다. 설정
  `bueongi.app.latest-version`(환경변수 `BUEONGI_APP_LATEST_VERSION`)에서 오고, 빈 값이면 정책이 없다. 판정 규칙은
  서버가 아니라 값으로 표현한다. 긴급 패치를 강제로 올리려면 min을 그 버전으로 올린다.

## `GET /api/places/search`: 장소 검색

Tmap POI 통합 검색으로 장소를 찾는다. 출처: `core-place/.../controller/PlaceController.kt`,
`PlaceSearchRequest.kt`, `PlaceSearchService.kt`, `clients/client-tmap-place/.../TmapPlaceClient.kt`.

요청: `?keyword=<검색어>`(query parameter)

200 응답(`List<PlaceResponse>`):

```json
[
  { "name": "서울시청", "address": "서울특별시 중구 세종대로 110", "lat": 37.5663, "lng": 126.9779 }
]
```

- `name`, `address`: 화면의 목적지 이름과 주소로 쓴다. `address`는 도로명 주소이며 없으면 빈 문자열이다.
- `lat`, `lng`: `POST /api/routes`의 `destination` 좌표로 바로 쓸 수 있다.

동작과 정책:

- 검색어는 앞뒤를 트림해서 외부 API에 넘긴다. 결과는 최대 15건이며 이름이나 좌표가 없는 POI는 뺀다.
- 트림 후 100자를 넘으면 외부 API를 부르기 전에 400 `INVALID_KEYWORD`로 막는다. 정확히 100자는 허용한다.
- 외부 호출이 실패하면 503 `PLACE_SEARCH_UNAVAILABLE`로 알린다. 근사 결과를 지어내지 않는다.

## `POST /api/routes`: 경로 옵션 목록

Tmap 실경로와 안심시설 실데이터로 safe, main, fast 경로를 만든다. 앱이 실제로 쓰는 경로 탐색이다. 출처:
`core-route/.../controller/RouteOptionController.kt`, `RouteRequest.kt`, `RouteOption.kt`,
`domain/service/RouteOptionService.kt`.

요청 본문(`RouteRequest`):

```json
{
  "origin": { "lat": 37.4979, "lng": 127.0276 },
  "destination": { "name": "서울시청", "lat": 37.5663, "lng": 126.9779 },
  "safetyPreference": "safe",
  "reroute": false
}
```

- `origin`, `destination`(필수): 좌표 범위를 벗어나면 400 `VALIDATION_FAILED`. `destination.name`은 선택이다.
- 출발-도착 직선거리가 1m 미만이면 422 `SAME_ORIGIN_DESTINATION`, 100km 초과면 422 `TRIP_TOO_FAR`.
- `safetyPreference`(선택): `balanced`, `safe`, `safest`. 생략하거나 모르는 값이면 `safe`다. 안심 우회 후보를 찾을
  때 허용하는 거리 예산을 정한다.
- `reroute`(선택, 기본 `false`): 경로 이탈 재탐색이면 `true`. 빨리 답하도록 안심 우회 후보를 찾지 않는다.
- `routeType`(선택): 받아서 해석은 하지만 응답에는 영향이 없다.

200 응답(`List<RouteOption>`): safe는 항상 있고, main과 fast는 고를 후보가 남을 때만 실린다. 각 항목은 기본 필드
`id`, `name`, `time`, `dist`, `desc`, `tags`, `type` 일곱에 `path`, `steps`, `score`, `markers`, `scoreDetail`,
`source`를 함께 싣는다.

Tmap 키가 없거나 후보 호출이 전부 실패하면 503 `ROUTE_SEARCH_UNAVAILABLE`로 알린다. 직선거리로 지어낸 경로를
내보내지 않는다.

- `type`: `safe`, `main`, `fast` 중 하나.
- `time`: Tmap 소요 시간을 분으로 반올림한 값(최소 1분), 예 `"12분"`.
- `dist`: 1000m 미만은 10m 단위 `"820m"`, 이상은 소수 한 자리 `"1.2km"`.
- `score`: 0~100. 주변에 어떤 안심시설 자료도 없으면 키가 빠진다(0점이 아니라 "없음"). 응답은 null 값 키를
  싣지 않는다(`@JsonInclude(NON_NULL)`).
- `scoreDetail.scoredItems`, `unscoredItems`: `cctv`, `lamp`, `bell`, `safehouse`, `police`, `safePath`. 자료가
  없는 항목은 빼고 남은 항목의 가중치로 다시 나눈다. `scoredItemRatio`는 자료가 있는 항목의 가중치 합(0~1)이다.
- `steps[].distanceMeters`, `timeSeconds`: 길안내 단계마다 다음 단계까지의 거리(m)와 시간(초).

## `GET /api/safety-spots/nearby`: 주변 안심시설

지정한 좌표 주변의 안심시설을 종류별로 세고, 지도에 그릴 마커를 돌려준다. 출처:
`core-safety-spot/.../controller/SafetySpotController.kt`, `NearbySafetySpotsRequest.kt`,
`NearbySafetySpotsResponse.kt`, `domain/nearby/service/NearbySafetySpotService.kt`.

요청: `?lat=<위도>&lng=<경도>`(필수). 범위 밖이면 400 `VALIDATION_FAILED`.

200 응답(`NearbySafetySpotsResponse`):

```json
{
  "counts": [ { "type": "cctv", "count": 14 } ],
  "spots": [
    { "type": "cctv", "lat": 37.4981, "lng": 127.0279, "name": "", "address": "", "phone": "" }
  ]
}
```

- `counts[].count`: 반경 500m 안의 시설 수.
- 반경 1km 안에 자료가 하나도 없는 종류는 `counts`와 `spots`에서 빠진다. 앱은 0 대신 "자료 없음"으로 그린다.
- `spots`: 종류마다 가까운 순으로 최대 10건. `name`, `address`, `phone`은 없으면 빈 문자열이다.
- `type`: `cctv`, `lamp`, `security_lamp`, `bell`, `safehouse`, `police`, `pedestrian_signal`. 범죄주의구간은
  지도 마커로 내보내지 않아 싣지 않는다.

## `/share`: 위치 공유(보호자)

귀가 중인 사용자가 공유 토큰을 발급받아 외부 메신저로 URL을 직접 공유하면, 보호자는 토큰 URL만으로(로그인 없이)
위치를 5초 폴링으로 본다. 다른 앱 API와 달리 상태가 있으며, Redis TTL로 1~3시간 후 자동 만료된다. 출처:
`core-share/.../controller/ShareController.kt`, `api/request/*`, `api/response/*`, `domain/service/ShareService.kt`,
`storage/db-share/.../store/RedisShareSessionStore.kt`.

- 경로 prefix: 보호자가 브라우저로 직접 여는 공개 URL이라 앱 전용 `/api`와 분리해 `/share` 아래에 둔다. CORS는
  `/api/**`와 같은 정책을 `/share/**`에도 적용한다.
- 저장소: `ShareSessionStore` 인터페이스의 운영 구현은 `RedisShareSessionStore` 하나다(`BUEONGI_REDIS_URL`). 만료는
  Redis 키 TTL이 강제한다. Redis에 접근하지 못하면 위치 공유 API만 503 `SHARE_STORE_UNAVAILABLE`을 돌려주고 다른 API는
  영향이 없다. Redis 키 모델은 [`LOCATION_SHARING_DESIGN.md`](LOCATION_SHARING_DESIGN.md)에 있다.
- 프라이버시: `token`은 128-bit URL-safe 랜덤 값(22자)이라 추측할 수 없다. 계정, 전화번호 같은 개인정보는 저장하지
  않는다. 상한 TTL 1~3시간이라 공유를 끄지 않아도 반드시 만료된다.

### `POST /share/create`: 공유 생성(사용자 단말)

요청 본문(`CreateShareRequest`, 본문 전체 선택):

```json
{ "expires_in_hours": 2, "eta_seconds": 1500 }
```

- `expires_in_hours`(선택): 만료까지의 시간(시). 1, 2, 3만 허용하고 범위 밖은 400 `VALIDATION_FAILED`. 생략하면(빈
  POST 포함) 1시간(`DEFAULT_EXPIRES_HOURS`)이다.
- `eta_seconds`(선택, 1 이상): 예상 도착까지 남은 시간(초). 미도착 감시(`MissedArrivalWatchdog`)의 기준이며 앱은 ETA를
  아는 경우에만 보낸다.

200 응답(`CreateShareResponse`):

```json
{
  "token": "qZ3kP...(128-bit URL-safe 토큰)",
  "owner_secret": "8Hf2v...(소유자만 갖는 비밀값)",
  "share_url": "http://localhost:8119/share/qZ3kP...",
  "expires_at": "2026-06-19T14:00:00Z"
}
```

- `owner_secret`: 위치와 경로를 올리거나 공유를 끝낼 때 소유자임을 증명하는 값. 공유 URL에도 조회 응답에도 실리지
  않으므로 앱이 단말에 보관한다.
- `share_url`: `{BUEONGI_SHARE_BASE_URL}/share/{token}`. 앱이 OS 공유 시트로 넘겨 사용자가 메신저로 직접 공유한다.

### `POST /share/{token}/location`: 위치 갱신(사용자 단말, 5초마다)

요청 본문(`UpdateLocationRequest`):

```json
{ "lat": 37.4979, "lng": 127.0276, "owner_secret": "8Hf2v...", "remaining_meters": 820, "remaining_seconds": 610 }
```

- `lat`, `lng`(필수): 범위 위반은 400 `VALIDATION_FAILED`, 비숫자 리터럴은 400 `MALFORMED_REQUEST`.
- `owner_secret`(필수): 비어 있으면 400 `VALIDATION_FAILED`, 생성 응답의 값과 다르면 404 `SHARE_SESSION_NOT_FOUND`.
- `remaining_meters`, `remaining_seconds`(선택, 0 이상): 목적지까지 남은 거리(m)와 시간(초). 백그라운드 위치
  태스크는 둘 다 없이 보낸다. 이미 설치된 옛 앱이 보내는 `remaining_m`, `remaining_s`도 받는다(응답은 새 키만 쓴다).
- `token`이 없거나 만료되면 404 `SHARE_SESSION_NOT_FOUND`.

200 응답(`UpdateLocationResponse`):

```json
{ "updated_at": "2026-06-19T06:30:00Z", "expires_at": "2026-06-19T14:00:00Z" }
```

### `GET /share/{token}/location`: 위치 조회(보호자 웹, 5초 폴링)

요청: path의 `token`만. 항상 200이며 상태는 `expired` 플래그와 좌표 null로 표현한다. 만료돼도 200이라 폴링 JS가 만료
화면을 그린다. 조회하면 보호자 시청 기록(12초)이 남는다.

| 상황                 | `lat`, `lng`, `updated_at` | `expired` | 보호자 웹 처리                      |
|----------------------|----------------------------|-----------|-------------------------------------|
| 위치가 1건 이상 있음 | 값 있음                    | `false`   | 지도에 위치 표시                    |
| 첫 위치 갱신 전      | `null`                     | `false`   | "위치 수신 대기 중" 표시, 폴링 지속 |
| 토큰 없음 또는 만료  | `null`                     | `true`    | 공유 종료/만료 안내, 폴링 중단      |

```json
{
  "lat": 37.4979,
  "lng": 127.0276,
  "updated_at": "2026-06-19T06:30:00Z",
  "expired": false,
  "remaining_meters": 820,
  "remaining_seconds": 610,
  "status": "in_progress"
}
```

- `remaining_meters`, `remaining_seconds`: 마지막 위치 갱신에 실린 값이며 없으면 null이다.
- `status`: 서버가 판정한 진행 상태다.
  - `in_progress`: 정상 진행.
  - `arrival_likely`: 예정 시각 초과와 위치 정지인데 마지막 위치가 목적지 반경 30m 안이다. 도착 후 공유만 끝내지 않은
    것으로 추정한다. 보호자 웹은 경고 대신 도착 추정 안내를 그린다.
  - `missed_arrival_suspect`: 예정 시각 초과와 위치 정지인데 목적지에서 멀다(미도착 의심).
- 예정 시각 초과는 `eta_seconds`에 60초 유예를 더한 예정 도착 뒤 300초가 지난 것이고, 위치 정지는 마지막 위치 갱신
  뒤 90초가 지난 것이다. 만료 응답의 `status`는 기본값(`in_progress`)이다. 만료는 `expired`가 계약이다.

### `POST /share/{token}/route`: 공유 경로 올리기(사용자 단말)

보호자 화면에 그릴 목적지, 경로, 경로변 시설을 올린다. 다시 올리면 덮어쓴다.

요청 본문(`SaveRouteRequest`):

```json
{
  "owner_secret": "8Hf2v...",
  "destination": { "name": "서울시청", "lat": 37.5663, "lng": 126.9779 },
  "path": [ { "lat": 37.4979, "lng": 127.0276 } ],
  "markers": [ { "type": "cctv", "lat": 37.4981, "lng": 127.0279, "x": 0.4, "y": 0.6, "camera_count": 2 } ],
  "total_distance_m": 1200,
  "total_time_s": 900,
  "type": "safe"
}
```

- `owner_secret`: 없거나 다르면 404 `SHARE_SESSION_NOT_FOUND`.
- `destination`(필수): 좌표 범위 검증. `name`은 선택이다.
- `path`: 좌표 목록, 최대 4000개. 생략하면 빈 목록이다.
- `markers`: 최대 300개. `type`은 `cctv`, `bell`, `store`, `police`, `safehouse`, `lamp`, `security_lamp`,
  `pedestrian_signal` 중 하나이고 `lat`, `lng`, `x`, `y`는 필수다. `name`, `purpose`, `camera_count`, `address`,
  `phone`은 선택이다.
- `total_distance_m`, `total_time_s`(필수, 0 이상): 전체 거리(m)와 시간(초).
- `type`(필수): `safe`, `main`, `fast` 중 하나. 그 밖의 값은 422 `INVALID_ROUTE_META`.
- 검증 위반은 400 `VALIDATION_FAILED`, 토큰이 없거나 만료되면 404 `SHARE_SESSION_NOT_FOUND`.

200 응답: `UpdateLocationResponse`(`updated_at`, `expires_at`).

### `GET /share/{token}/route`: 경로 조회(보호자 웹)

항상 200이다(`RouteView`).

- 토큰 없음 또는 만료: `{ "expired": true, "route": null }`
- 경로를 아직 올리지 않음: `{ "expired": false, "route": null }`
- 경로 있음: `route`에 올린 값(`destination`, `path`, `markers`, `total_distance_m`, `total_time_s`, `type`)이
  그대로 실린다. 값이 없는 선택 칸은 null이다.

### `POST /share/{token}/end`: 공유 종료(사용자 단말)

요청 본문(`EndShareRequest`): `{ "owner_secret": "8Hf2v..." }`. `owner_secret`이 비어 있으면 400
`VALIDATION_FAILED`.

- 성공하면 200(본문 없음)이며 공유, 위치, 경로, 시청 기록 키를 함께 지운다.
- 토큰이 없거나 이미 만료됐으면 아무것도 하지 않고 200이다.
- `owner_secret`이 다르면 404 `SHARE_SESSION_NOT_FOUND`.

### `POST /share/{token}/watching`: 보호자 시청 여부(사용자 단말)

요청 본문(`WatchRequest`): `{ "owner_secret": "8Hf2v..." }`. `owner_secret`이 비어 있으면 400
`VALIDATION_FAILED`, 다르면 404 `SHARE_SESSION_NOT_FOUND`.

200 응답(`WatchResponse`):

```json
{
  "watching": true,
  "lat": 37.4979,
  "lng": 127.0276,
  "updated_at": "2026-06-19T06:30:00Z",
  "expired": false,
  "remaining_meters": 820,
  "remaining_seconds": 610
}
```

- `watching`: 보호자가 최근 12초 안에 `GET /share/{token}/location`을 호출했는가. 보호자 웹의 폴링 주기(5초)에 맞춘
  판정 창이다.
- `watching`과 `expired` 외의 칸은 값이 없으면 null이다.
- 토큰이 없거나 만료되면 `{ "watching": false, "expired": true, ... }`(나머지 null)로 200을 돌려준다.

### `GET /share/{token}`: 보호자 지도 페이지(HTML)

200 `text/html`. 지도(Leaflet과 OpenStreetMap 타일, 지도 SDK 키 불필요)와 5초 폴링 JS를 담은 보호자 페이지다. 토큰
유효성과 무관하게 페이지를 주고, 폴링 JS가 `GET /share/{token}/location`의 `expired`로 만료, 대기, 실시간 상태를
그린다. 토큰은 JS 문자열 문맥으로 이스케이프해 넣는다. 템플릿: `core-share/src/main/resources/share/guardian.html`.

## 관리자 인증

출처: `core-admin-auth/.../controller/AdminAuthController.kt`, `AdminIdentityController.kt`,
`domain/config/SecurityConfig.kt`.

- `POST /api/v1/auth/admin/login`: 본문 `{ "loginId": "...", "password": "..." }`. 성공하면 `token`
  (`accessToken`, `tokenType`, `expiresIn`, `refreshToken`, `refreshExpiresIn`)과 `user`(`id`, `provider`, `email`,
  `roles`)를 돌려준다. 실패하면 401 `UNAUTHORIZED`.
- `POST /api/v1/auth/local/token/refresh`: 본문 `{ "refreshToken": "..." }`. 새 `token`을 돌려준다.
- `GET /api/admin/me`: `{ "userId": 1, "roles": ["ROLE_ADMIN"] }` 형태로 토큰의 관리자 정보를 돌려준다.
- `/api/admin/**`는 `Authorization: Bearer <access token>`이 필요하다. 없거나 유효하지 않으면 401 `UNAUTHORIZED`,
  관리자 권한이 없으면 403 `FORBIDDEN`. refresh token으로는 관리자 API를 부를 수 없다.

## `GET /api/admin/alerts`: 장애 알림 이력(관리자)

최신순 알림 이력이다. `limit`(기본 100)은 1~500으로 잘라 쓴다. 출처:
`core-admin-ops/.../controller/AlertController.kt`, `api/response/AlertResponse.kt`. 각 항목의 종류, 이름, 심각도는
서버가 알림 key의 `:` 앞부분으로 `AlertCategory`(아래 표)를 찾아 싣고, 화면은 그대로 그린다.

200 응답(`AlertsResponse`):

```json
{
  "alerts": [
    {
      "atEpochMs": 1720000000000,
      "key": "safety-spot-fetch-failed:cctv",
      "title": "CCTV 수집 실패",
      "detail": "...",
      "category": "safety-spot-fetch-failed",
      "label": "시설 수집 실패",
      "severity": "degraded"
    }
  ]
}
```

- `severity`: `critical`(즉시 확인), `degraded`(부분 장애), `unknown` 중 하나. 표에 없는 key면 `unknown`이고
  `category`와 `label`은 key의 `:` 앞부분 그대로다.

## `/api/admin/slack-config`: Slack 알림 설정(관리자)

운영 알림(Slack)의 발송 on/off, 스로틀, 카테고리별 뮤트를 웹에서 편집한다. 설정은 메모리에만 있어 재시작하면 환경변수
값으로 돌아간다. 출처: `core-admin-ops/.../controller/SlackConfigController.kt`,
`domain/slack/service/SlackConfigService.kt`, `core-enum/.../AlertCategory.kt`,
`clients/client-slack/.../SlackAlertConfig.kt`.

- webhook은 편집할 수 없다. webhook URL은 환경변수 시크릿(`SLACK_WEBHOOK_URL` -> `bueongi.alert.slack-webhook-url`)
  이라 편집 대상이 아니며, 조회할 때도 원문 대신 마스킹 값만 돌려준다. `/services/` 형식이면
  `https://hooks.slack.com/services/T***/B***/***`처럼 가리고, 형식이 다르면 앞 8자만 남기고 `***`를 붙인다.
- 실제 Slack 전송 조건은 `webhookConfigured && enabled`다. 뮤트된 카테고리는 발송만 생략하고 장애 이력
  (`GET /api/admin/alerts`)에는 그대로 남는다.
- 스로틀 기본값은 `SLACK_ALERT_THROTTLE_MINUTES`(기본 10분)이다.

### 알림 카테고리

카테고리는 8종이며 `core-enum`의 `AlertCategory`가 정본이다. 알림을 보내는 곳도 같은 enum으로 key를 만들므로, 아래
key는 실제 알림 key의 `:` 앞부분과 항상 일치한다.

| key                          | label                  | severity   | 실제 알림 key 예                       |
|------------------------------|------------------------|------------|----------------------------------------|
| `api-500`                    | 서버 오류(미처리 500)  | `critical` | `api-500:<예외 클래스>`                |
| `place-search-failed`        | 장소 검색 실패         | `degraded` | `place-search-failed`                  |
| `tmap-route-failed`          | 경로 검색 실패         | `degraded` | `tmap-route-failed`                    |
| `missed-arrival`             | 미도착 의심            | `critical` | `missed-arrival:<가린 토큰>`           |
| `arrival-likely`             | 도착 추정(공유 미종료) | `degraded` | `arrival-likely:<가린 토큰>`           |
| `share-store-unavailable`    | 위치 공유 저장소 장애  | `critical` | `share-store-unavailable`              |
| `safety-spot-fetch-failed`   | 시설 수집 실패         | `degraded` | `safety-spot-fetch-failed:<cdnType>`   |
| `safety-spot-no-coordinates` | 시설 좌표 없음         | `degraded` | `safety-spot-no-coordinates:<cdnType>` |

### `GET /api/admin/slack-config`: 현재 설정 조회

200 응답(`SlackConfigResponse`):

```json
{
  "webhookConfigured": true,
  "webhookMasked": "https://hooks.slack.com/services/T***/B***/***",
  "enabled": true,
  "throttleMinutes": 10,
  "categories": [
    { "key": "api-500", "label": "서버 오류(미처리 500)", "muted": false },
    { "key": "place-search-failed", "label": "장소 검색 실패", "muted": false },
    { "key": "tmap-route-failed", "label": "경로 검색 실패", "muted": false },
    { "key": "missed-arrival", "label": "미도착 의심", "muted": false },
    { "key": "arrival-likely", "label": "도착 추정(공유 미종료)", "muted": false },
    { "key": "share-store-unavailable", "label": "위치 공유 저장소 장애", "muted": false },
    { "key": "safety-spot-fetch-failed", "label": "시설 수집 실패", "muted": false },
    { "key": "safety-spot-no-coordinates", "label": "시설 좌표 없음", "muted": false }
  ]
}
```

- `webhookConfigured`: webhook 환경변수 설정 여부. `webhookMasked`: 마스킹 URL이며 미설정이면 빈 문자열이다.
- `enabled`: 마스터 발송 토글. webhook이 있으면 `true`로 시작한다. `throttleMinutes`: 같은 key 재발송 억제 창(분).
- `categories`: 항상 위 8종이 `AlertCategory` 선언 순서로 실리고, 각 항목에 `muted` 여부가 있다.

### `PUT /api/admin/slack-config`: 설정 편집

요청 본문(`SlackConfigUpdateRequest`):

```json
{ "enabled": true, "throttleMinutes": 30, "mutedCategories": ["api-500", "tmap-route-failed"] }
```

- `enabled`(필수): 마스터 발송 토글. 누락하면 400 `VALIDATION_FAILED`.
- `throttleMinutes`(필수): 1~1440. 범위 밖이면 400 `VALIDATION_FAILED`.
- `mutedCategories`(선택, 기본 `[]`): 뮤트할 카테고리 key 목록. 위 8종 밖의 key가 하나라도 있으면 422
  `INVALID_SLACK_CATEGORY`로 전체를 거부한다(부분 적용 없음).
- webhook은 본문에 없으므로 편집할 수 없다.

200 응답: 갱신된 `SlackConfigResponse`(GET과 같은 형태).

### `POST /api/admin/slack-config/test`: 테스트 발송

발송할 수 있는 상태(`webhookConfigured && enabled`)면 테스트 메시지를 동기로 한 번 보낸다(스로틀과 뮤트 무시). 발송
실패, 예외, 비활성은 모두 `sent: false`이며 예외를 던지지 않는다.

요청: 본문 없음.

200 응답(`SlackTestResponse`):

```json
{ "sent": true, "enabled": true }
```

- `sent`: 테스트 발송이 성공했는가.
- `enabled`: 현재 발송할 수 있는 상태인가(`webhookConfigured && enabled`).

## 도메인 상수

| 상수                           |     값 | 단위 | 출처                       |
|--------------------------------|-------:|------|----------------------------|
| `MAX_KEYWORD_LENGTH`           |    100 | 자   | `PlaceSearchRequest`       |
| `MIN_TRIP_DISTANCE_METERS`     |      1 | m    | `RouteRequest`             |
| `MAX_TRIP_DISTANCE_METERS`     | 100000 | m    | `RouteRequest`             |
| `MAX_MARKERS_PER_TYPE`(경로)   |     12 | 개   | `RouteMarkerBuilder`       |
| `NEARBY_RADIUS_METERS`         |    500 | m    | `NearbySafetySpotService`  |
| `DATA_PRESENCE_RADIUS_METERS`  |   1000 | m    | `NearbySafetySpotService`  |
| `MAX_SPOTS_PER_TYPE`(주변)     |     10 | 개   | `NearbySafetySpotService`  |
| `MIN_EXPIRES_HOURS`            |      1 | 시간 | `CreateShareRequest`       |
| `MAX_EXPIRES_HOURS`            |      3 | 시간 | `CreateShareRequest`       |
| `DEFAULT_EXPIRES_HOURS`        |      1 | 시간 | `SharePolicy.kt`           |
| `MISSED_ARRIVAL_GRACE_SECONDS` |     60 | 초   | `SharePolicy.kt`           |
| `OVERDUE_SECONDS`              |    300 | 초   | `SharePolicy.kt`           |
| `LOCATION_STOPPED_SECONDS`     |     90 | 초   | `SharePolicy.kt`           |
| `ARRIVAL_RADIUS_METERS`        |     30 | m    | `SharePolicy.kt`           |
| `VIEWER_WINDOW_SECONDS`        |     12 | 초   | `ShareSessionStore`        |
| `MAX_ROUTE_PATH_POINTS`        |   4000 | 개   | `SaveRouteRequest`         |
| `MAX_ROUTE_MARKERS`            |    300 | 개   | `SaveRouteRequest`         |
| `MIN_THROTTLE_MINUTES`         |      1 | 분   | `SlackAlertConfig`         |
| `MAX_THROTTLE_MINUTES`         |   1440 | 분   | `SlackAlertConfig`         |
| `HISTORY_MAX_LIMIT`            |    500 | 건   | `AlertNotifier`            |
| `MAX_PAGE_SIZE`(CSV 쪽)        |    200 | 행   | `SafetySpotCsvFileService` |
