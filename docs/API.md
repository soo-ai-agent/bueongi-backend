# bueongi backend — API 계약 레퍼런스

> 안심(안심) 귀가 앱 백엔드의 단일 API 명세. **프론트(bueongi-dev) 연동용 참조 문서.**
> 이 문서는 코드(컨트롤러·DTO·서비스·상수)에서 직접 파생했다. 출처 파일을 각 절에 명시하니,
> 계약 변경 시 **코드와 이 문서를 함께** 갱신할 것(드리프트 방지).
>
> 누적 변경 이력/설계 배경은 `.claude/memories/consensus.md`(루프 베이턴)에 있다. 이 문서는 **현재 계약의 스냅샷**만 담는다.

## 개요

- **Base URL(dev)**: `http://localhost:8119`
- **포트**: `8119` (`application.yml: server.port`)
- **CORS**: `/api/**` 는 `BUEONGI_CORS_ORIGINS`(콤마 구분, 기본 dev `http://localhost:3619`)만, 메서드 `GET/POST/OPTIONS` 만 허용. (`config/WebCorsConfig.kt`)
- **모든 엔드포인트는 stateless·결정론적**: 같은 입력 → 항상 같은 응답. routeId 가 아니라 좌표 기반이라 새로고침/직접진입에도 끊기지 않는다.
- **외부 키 없이 동작(MVP)**: 장소 검색은 Kakao 키 미주입 시 내장 시드 카탈로그로 결정론적 응답. 키 주입 시 내부만 교체되고 응답 계약(아래)은 동일 — 프론트는 무변경.
- 좌표 규약: 위도 `lat` -90~90, 경도 `lng` -180~180.

---

## 공통 에러 스키마 (RFC 7807 ProblemDetail + `code`)

검증/파싱/도메인/프레임워크 실패는 **모두** 아래 스키마로 응답한다. 출처: `common/ApiExceptionHandler.kt`.

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

- **`code`**: 머신리더블 분기 키. **모든 에러 응답에 항상 존재**(검증·도메인·프레임워크·404·500 전부) — 프론트는 `status` 대신 `code` 로 분기할 것.
- **`errors[]`**: 필드 단위 사유(있는 code 만). `field` 는 중첩 시 `origin.lat`/`destination.lng` 형태. `rejectedValue` 는 거부된 값(또는 길이/거리 등 파생값).

### 에러 code 전체 표

| code | HTTP | title | errors | 발생 조건 / 프론트 처리 |
|------|------|-------|--------|------------------------|
| `VALIDATION_FAILED` | 400 | Validation Failed | ✅ field별 | `@Valid` 좌표 범위 위반(lat ±90·lng ±180 초과), 좌표 오버플로(`1e400`→±Infinity). 좌표 입력 인라인 에러 표시. |
| `MALFORMED_REQUEST` | 400 | Malformed Request | ❌ | 빈 본문·깨진 JSON·타입 불일치·`NaN`/`Infinity` 비숫자 리터럴(Jackson 거부). |
| `INVALID_KEYWORD` | 400 | Invalid Keyword | ✅ `keyword`(rejectedValue=입력 길이) | 검색어가 트림 후 100자 초과. 검색어 줄이도록 안내. |
| `SAME_ORIGIN_DESTINATION` | 422 | Same Origin And Destination | ✅ `destination`(rejectedValue=직선거리m) | 출발-도착 직선거리 < 1m(사실상 같은 지점). 도착지 재선택 유도. |
| `TRIP_TOO_FAR` | 422 | Trip Too Far | ✅ `destination`(rejectedValue=직선거리m) | 출발-도착 직선거리 > 100km(도착지 오선택/위경도 뒤바뀜 추정). 도착지 재선택 유도("100km 이내" 안내). |
| `INVALID_SLACK_CATEGORY` | 422 | Invalid Slack Category | ✅ `mutedCategories`(rejectedValue=알 수 없는 key 목록) | 관리자 Slack 설정 편집(PUT `/api/admin/slack-config`)의 `mutedCategories` 에 알려진 5종 밖 key. 허용 카테고리만 뮤트하도록 안내. |
| `METHOD_NOT_ALLOWED` | 405 | Method Not Allowed | ❌ | POST 전용 엔드포인트에 GET 등. 응답에 `Allow` 헤더(허용 메서드) 보존. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Unsupported Media Type | ❌ | JSON 본문 엔드포인트에 `text/plain` 등 비-JSON Content-Type. |
| `SHARE_SESSION_NOT_FOUND` | 404 | Share Session Not Found | ❌ | 위치 공유 `token` 이 없거나 TTL 만료된 상태에서 **위치 갱신(POST `/share/{token}/location`)** 을 시도. 미매칭 경로(`NOT_FOUND`)와 구분되는 **도메인 404**. (보호자 조회 GET `/share/{token}/location` 은 404 대신 항상 200 + `expired:true`.) |
| `NOT_FOUND` | 404 | Not Found | ❌ | 매핑된 핸들러/정적 리소스 없는 경로(오타·폐기된 엔드포인트). |
| `INTERNAL_ERROR` | 500 | Internal Server Error | ❌ | 미처리 예외. **응답 본문에 내부 details(스택트레이스·예외 메시지) 비노출**(보안) — 전체 스택트레이스는 서버 로그에만. |

---

## `GET /api/health` — 운영 readiness

서비스 가동 여부 + 활성 장소 검색 소스(시크릿 비노출)를 알려준다. 출처: `health/HealthController.kt`.

**요청**: 없음.

**200 응답** (`HealthStatus`):

```json
{ "status": "UP", "service": "bueongi-backend", "locationStore": "memory" }
```

- `status`: 항상 `"UP"`(요청 처리 가능하면 도달).
- `service`: `"bueongi-backend"`.
- `locationStore`: `"redis"`=Redis 위치공유 저장소 / `"memory"`=인메모리 폴백(`BUEONGI_REDIS_URL` 미주입 시 MVP 기본). **저장소 종류 라벨일 뿐 Redis URL·자격증명은 절대 노출하지 않는다.** (위치 공유 섹션 참조)

---

## `GET /api/app-info` — 스플래시용 서버 버전

모바일 시작 화면이 **한 번 호출**로 서버 버전을 얻는다(응답이 오면 서버가 살아 있다는 뜻이기도 하다). 출처: `api/AppInfoController.kt`.

**요청**: 없음.

**200 응답** (`AppInfoResponse`):

```json
{
  "serverVersion": "0.0.1-SNAPSHOT"
}
```

- `serverVersion`: 빌드 정보(`build-info.properties`, Gradle `springBoot { buildInfo() }` 가 만든다)에서 온다.
  빌드 정보가 없으면 서버가 뜨지 않는다 — 빈 버전이 몰래 나가지 않게 하려는 것이다.
  IntelliJ 로 실행할 때는 Gradle 로 빌드하도록(기본값) 둔다.

---

## `GET /api/places/search` — 장소 검색

이름/주소 부분일치 검색. 출처: `places/PlaceController.kt`, `places/PlaceSearchService.kt`, `places/PlaceDtos.kt`.

**요청**: `?keyword=<검색어>` (query param, optional)

**200 응답** (`List<PlaceItem>`):

```json
[
  { "name": "서울시청", "address": "서울특별시 중구 세종대로 110", "lat": 37.5663, "lng": 126.9779 }
]
```

- `name`/`address`: 프론트 `Destination{name,address}` 로 매핑.
- `lat`/`lng`: `POST /api/routes` 의 `destination` 좌표로 바로 사용 가능(additive, 무시해도 무방).

**동작/정책**:

- 매칭: 이름·주소 부분일치, 앞뒤 트림 + 라틴 대소문자 무시.
- 트림 후 **빈 검색어 → 빈 목록 `[]`**(에러 아님 — 프론트는 '최근 검색'으로 폴백). 추측 결과를 만들지 않는다.
- 트림 후 **100자 초과 → 400 `INVALID_KEYWORD`**(외부 API 호출 전에 차단). 경계(정확히 100자)는 허용. (`MAX_KEYWORD_LENGTH=100`)
- Kakao Local 실검색만 쓴다. 키가 없거나 호출이 실패하면 **503 `PLACE_SEARCH_UNAVAILABLE`** 로 알린다 — 근사 결과를 지어내지 않는다.

---

## `POST /api/routes` — 경로 옵션 목록 (앱이 실제로 쓰는 경로 탐색)

Tmap 실경로 + 안심시설 실데이터로 safe/main/fast 3개를 만든다. 요청 본문은 `compare` 와 같은 `RouteRequest` 이고, 앱은 이 경로만 호출한다. 출처: `core-route/api/controller/RouteController.kt`, `domain/service/SafeRouteService.kt`.

- `safetyPreference` (optional): `balanced`/`safe`/`safest`. 생략하면 백엔드 기본 성향.
- `reroute` (optional, 기본 `false`): 경로 이탈 재탐색이면 `true`. 외부 호출 예산을 달리 쓴다(`RouteCandidateCollector`).

**200 응답** (`List<RouteOption>`): 유형별 7개 기본 필드에 `path`·`steps`·`score`·`markers`·`breakdown`·`provenance` 여섯이 **함께** 실린다.

Tmap 키가 없거나 후보 호출이 전부 실패하면 **503 `ROUTE_SEARCH_UNAVAILABLE`** 로 알린다 — 직선거리로 지어낸 경로를 내보내지 않는다.

- `score`: 0~100. 주변에 어떤 안심시설 자료도 없으면 `null`(0점이 아니라 '없음').
- `breakdown.scoredAxes`/`unscoredAxes`: `cctv`/`lamp`/`bell`/`safehouse`/`police`/`safePath`. 자료가 없는 축은 빼고 남은 축의 가중치로 다시 나눈다.

---

## `/share` — 최소 위치 공유 (보호자, MVP)

귀가 중인 사용자가 공유 토큰을 발급받아 **외부 메신저로 URL 을 직접 공유**하면, 보호자는 토큰 URL 만으로(로그인 없이) 위치를 5초 폴링으로 본다. **다른 엔드포인트와 달리 stateful** — Redis TTL 로 1~3시간 후 자동 만료된다. 출처: `share/ShareController.kt`, `share/ShareDtos.kt`, `share/ShareService.kt`, `share/LocationStore.kt`, `share/RedisLocationStore.kt`, `share/InMemoryLocationStore.kt`.

> **경로 prefix**: 보호자가 브라우저로 직접 여는 공개 URL 이라 앱 전용 `/api` 와 분리해 `/share` 하위에 둔다. CORS 는 `/api/**` 와 동일 정책으로 `/share/**` 에도 허용(별도 origin 보호자 웹의 교차 출처 폴링 지원).
> **저장소**: `LocationStore` 추상화(`PlaceProvider` 패턴 차용). `BUEONGI_REDIS_URL` 주입 시 `RedisLocationStore`, 미주입(로컬/CI/MVP) 시 `InMemoryLocationStore` 폴백. 활성 저장소는 `GET /api/health.locationStore`(`"redis"`/`"memory"`)로 확인.
> **Redis 키 모델**: `share:{token}` HASH `{ created_at, expires_at }` + `location:{token}` HASH `{ lat, lng, updated_at }`. 둘 다 동일 상한 TTL(1~3h)로 함께 자동 삭제 — 공유가 만료되면 위치도 잔존하지 않는다.
> **프라이버시**: `token` 은 128-bit URL-safe 랜덤(추측 불가). 계정·전화번호 등 PII 미저장. 1~3시간 상한 TTL 로 무기한 추적을 막는다 — 공유를 끄지 않아도 반드시 만료된다. 보호자 회원가입/페어링/푸시/서버 발송 메시지는 **범위 밖**.

### `POST /share/create` — 공유 생성 (사용자 단말)

**요청 본문** (`CreateShareRequest`, **본문 전체 optional**):

```json
{ "expires_in_hours": 2, "eta_seconds": 1500 }
```

- `expires_in_hours` (optional): 만료까지의 시간(시). **1·2·3 만 허용**(범위 밖 → 400 `VALIDATION_FAILED`). 생략(빈 POST 포함) → 1시간(`DEFAULT_EXPIRES_HOURS`, 가장 보수적).
- `eta_seconds` (optional, 1 이상): 예상 도착까지 남은 시간(초). 미도착 감시(`MissedArrivalWatchdog`)의 기준이며 앱은 ETA 를 아는 경우에만 보낸다.

**200 응답** (`CreateShareResponse`):

```json
{
  "token": "qZ3kP...(128-bit URL-safe 토큰)",
  "owner_secret": "8Hf2v...(소유자만 갖는 비밀값)",
  "share_url": "http://localhost:8119/share/qZ3kP...",
  "expires_at": "2026-06-19T14:00:00Z"
}
```

- `owner_secret`: 공유를 끝내거나 경로를 올릴 때 소유자임을 증명하는 값. 공유 URL 에도 조회 응답에도 실리지 않으므로 앱이 단말에 보관한다.
- `share_url`: 앱이 OS 공유 시트로 넘겨 사용자가 카카오톡/문자 등으로 직접 공유. base 는 `BUEONGI_SHARE_BASE_URL`(운영은 공개 도메인).

### `POST /share/{token}/location` — 위치 갱신 (사용자 단말, 5초마다)

**요청 본문** (`UpdateLocationRequest`):

```json
{ "lat": 37.4979, "lng": 127.0276, "owner_secret": "8Hf2v...", "remaining_m": 820, "remaining_s": 610 }
```

- `lat`/`lng`: **필수**. 좌표 규약(lat ±90·lng ±180) + 비유한(`NaN`/±`Infinity`) 거부. 범위 위반 → 400 `VALIDATION_FAILED`, 비숫자 리터럴 → 400 `MALFORMED_REQUEST`.
- `owner_secret`: **필수**. 생성 응답의 값과 다르면 404(불투명 응답).
- `remaining_m`/`remaining_s` (optional, 0 이상): 목적지까지 남은 거리·시간. 백그라운드 위치 태스크는 둘 다 없이 보낸다.
- `token` 없음/만료 → **404 `SHARE_SESSION_NOT_FOUND`**.

**200 응답** (`UpdateLocationResponse`):

```json
{ "updated_at": "2026-06-19T06:30:00Z", "expires_at": "2026-06-19T14:00:00Z" }
```

### `GET /share/{token}/location` — 위치 조회 (보호자 웹, 5초 폴링)

**요청**: path 의 `token` 만. **항상 200** — 상태는 `expired` 플래그와 좌표 null 로 표현한다(만료 시에도 200 으로 폴링 JS 가 만료 화면을 그린다).

| 본문 | 의미 | 보호자 웹 처리 |
|------|------|----------------|
| `{ "lat":37.4, "lng":127.0, "updated_at":"...", "expired":false }` | 위치 1건 이상 갱신됨 | 지도에 위치 표시 |
| `{ "lat":null, "lng":null, "updated_at":null, "expired":false }` | 공유는 살아있으나 **첫 위치 갱신 전** | "위치 수신 대기 중" 표시, 폴링 지속 |
| `{ "lat":null, "lng":null, "updated_at":null, "expired":true }` | 토큰 없음 또는 TTL 만료 | "공유가 종료/만료되었습니다" 안내, 폴링 중단 |

### `POST /share/{token}/end` — 공유 종료 (사용자 단말)

**요청 본문** (`EndShareRequest`): `{ "owner_secret": "8Hf2v..." }` — `owner_secret` 필수.

**200**. 토큰이 없거나 이미 만료면 404 지만, 앱은 종료 의도가 이미 이뤄진 것으로 보고 404 를 성공처럼 다룬다.

### `POST /share/{token}/watching` — 보호자 시청 여부 (사용자 단말)

**요청 본문** (`WatchRequest`): `{ "owner_secret": "8Hf2v..." }` — `owner_secret` 필수.

**200 응답** (`WatchResponse`):

```json
{ "watching": true, "lat": 37.4979, "lng": 127.0276, "updated_at": "...", "expired": false, "remaining_m": 820, "remaining_s": 610 }
```

- `watching` 외 모든 칸은 값이 없으면 null 로 나간다(`expired` 는 기본 `false`).
- 보호자가 최근에 조회했는지를 사용자 화면에 보여주는 용도라, 판정 창은 보호자 웹의 폴링 주기(5초)에 맞춰져 있다.

### `GET /share/{token}` — 보호자 지도 페이지 (HTML)

**200** `text/html`. 지도(Leaflet + OpenStreetMap 타일, **지도 SDK 키 불요**) + 5초 폴링 JS 를 담은 보호자 페이지. 토큰 유효성과 무관하게 페이지를 주고, 폴링 JS 가 위 `GET location` 의 `expired` 로 만료/대기/라이브 상태를 그린다. 정적 리소스: `resources/share/guardian.html`.

---

## `GET /api/admin/traffic` — 수신 트래픽 (관리자)

`/api/admin/*` 는 SecurityConfig 가 `ROLE_ADMIN` 으로 보호한다(Bearer JWT). **실제로 들어온 수신 요청**(`/api/**`·`/share/**`)을 창(window, 최근 `REQUEST_METRIC_CAP`=5000 건) 단위로 집계해, 엔드포인트별 호출수와 **예측 사용자 수**(고유 익명 client-id 관측치)를 돌려준다. 계측은 `RequestMetricsFilter` 가 체인 실행 후 남기는 순수 부수효과라 실제 요청은 불변이며, 저장소 미주입(테스트 슬라이스)이어도 무해하다. 출처: `api/admin/AdminController.kt`, `domain/admin/RequestMetricsService.kt`, `api/config/RequestMetricsFilter.kt`·`RequestPathNormalizer.kt`, `storage/db-core/.../requestmetric/`.

**계측 대상/제외**: 대상은 `/api/**`·`/share/**`. 제외는 `/api/admin/**`(관리자 자기 트래픽은 사용자 집계에서 뺀다)·`/actuator`·`/healthz`·`/cdn`. 동적 경로는 **정규화 템플릿**으로 집계한다 — `/share/<토큰>`·`/share/<토큰>/{location,end,watching,route}` 의 토큰 세그먼트만 `{token}` 으로 치환하고(`/share/create`·`/api/**` 는 원경로 유지), 그 외 경로도 그대로 기록한다(누락 없음).

**예측 사용자(client-id)**: 프론트가 보내는 `X-Client-Id`(익명 식별자) 헤더로 고유 수를 관측한다. **로그인 사용자 수가 아니라 관측 하한**이다 — `X-Client-Id` 가 없는 요청은 고유 사용자 집계에서 제외되며, 그 호출 수를 `estimatedUsers.callsWithoutClientId` 로 정직하게 함께 노출한다(호출수 자체에는 포함).

**200 응답** (`TrafficResponse`):

```json
{
  "totalCalls": 842,
  "windowSize": 842,
  "byEndpoint": [
    { "method": "GET", "endpoint": "/share/{token}/location", "count": 500, "successRate": 1.0, "avgDurationMs": 8, "p95DurationMs": 15, "distinctClients": 37 },
    { "method": "GET", "endpoint": "/api/places/search", "count": 210, "successRate": 0.98, "avgDurationMs": 120, "p95DurationMs": 340, "distinctClients": 44 }
  ],
  "estimatedUsers": { "today": 12, "last7d": 58, "last30d": 143, "callsWithoutClientId": 61, "newToday": 4, "returningToday": 8 },
  "latency": { "p50Ms": 12, "p95Ms": 210, "p99Ms": 540 },
  "hourlyCalls": [ { "hourStartMs": 1720828800000, "count": 33 } ],
  "hourlyErrors": [ { "hourStartMs": 1720828800000, "count": 1 } ],
  "dailyActiveUsers": [ { "dateEpochMs": 1720710000000, "distinctClients": 21 } ]
}
```

| 필드 | 의미 |
|------|------|
| `totalCalls` | 집계 창 내 총 수신 호출 수(clientId 유무 무관 전량). |
| `windowSize` | 집계에 쓴 기록 수(창 상한 이하). |
| `byEndpoint` | 메서드+정규화경로별 집계, **`count` 내림차순**. `successRate`=status 2xx·3xx 비율(0~1), `avgDurationMs`=평균 소요(반올림), `p95DurationMs`=그룹 내 소요시간 95 분위수(ms, nearest-rank), `distinctClients`=고유 익명 client-id 수(null 제외). |
| `estimatedUsers.today` | 오늘(Asia/Seoul 자정 이후) 고유 client-id 수. |
| `estimatedUsers.last7d` / `last30d` | 최근 7일 / 30일(이동 창) 고유 client-id 수. |
| `estimatedUsers.callsWithoutClientId` | `X-Client-Id` 없어 사용자 추정에서 제외된 호출 수(정직성 지표). |
| `estimatedUsers.newToday` / `returningToday` | 오늘 활동한 client-id 중 **창 내 최초 관측이 오늘**이면 신규, **오늘 이전**이면 재방문. `today = newToday + returningToday`. **근사**: 진짜 첫 방문이 아니라 창(`REQUEST_METRIC_CAP`=5000) 안에 남아 있는 기록 중 가장 이른 것 기준이라, 오래된 사용자가 창에서 밀려나면 신규로 잡힐 수 있다. |
| `latency` | 전체 소요시간 분위수 `p50Ms`/`p95Ms`/`p99Ms`(ms, nearest-rank·보간 없음). 빈 창이면 전부 0. |
| `hourlyCalls` | 최근 24시간 1시간 버킷(오래된→최신, Asia/Seoul 정각). `hourStartMs`=버킷 시작 epoch ms, 호출 0건 버킷도 노출. |
| `hourlyErrors` | 최근 24시간 시간대별 **에러(status 2xx·3xx 아님) 건수** — `hourlyCalls` 와 **같은 경계·같은 버킷**이라 호출량 위에 겹쳐 그릴 수 있다(에러 0건 시간도 노출). |
| `dailyActiveUsers` | 최근 7일 일자별 고유 사용자 수(오래된→최신). `dateEpochMs`=해당 날 자정(Asia/Seoul), 요청 0건 날도 `distinctClients:0` 으로 노출. |

- **정직성**: 모든 값은 실제 수신 기록만으로 계산한다 — 목/샘플/하드코딩 없음. `estimatedUsers` 는 익명 client-id 관측치(관측 하한)이지 실제 사용자 수 추정이 아니다.

---

## `GET /api/admin/api-status` — 외부 API 원천별 상태·쿼터 (관리자)

`/api/admin/*` 는 SecurityConfig 가 `ROLE_ADMIN` 으로 보호한다(Bearer JWT). 저장된 외부 API 접속 내역(창, 최근 `API_CALL_LOG_CAP`=1000 건)을 **원천(provider)별로** 집계해 성공률·평균/95분위 소요시간·최근 성공/실패 시각·오늘 호출 수(쿼터)·시간별 호출빈도를 돌려준다. 원천은 4종(`KAKAO`/`TMAP`/`DATAGOKR`/`SAFEMAP`) **고정 노출**(로그 0건도 `total:0`). 집계만 한다 — 새 저장소·새 수집 없음. 출처: `api/admin/AdminController.kt`, `domain/admin/ApiStatusService.kt`, `storage/db-core/.../apilog/`.

**200 응답** (`ApiStatusSummary`):

```json
{
  "windowSize": 640,
  "providers": [
    {
      "provider": "DATAGOKR", "total": 210, "successCount": 208, "successRate": 0.99,
      "avgDurationMs": 180, "p95DurationMs": 520, "lastSuccessAtMs": 1720828800000, "lastFailureAtMs": 1720820000000,
      "todayCount": 120, "quotaLimit": 1000, "quotaWarning": false,
      "quotaBurnPerHour": 12.6, "quotaProjectedTodayTotal": 302,
      "hourlyCalls": [ { "hourStartMs": 1720828800000, "count": 7 } ]
    }
  ],
  "hourlyTrend": [ { "hourStartMs": 1720828800000, "count": 21 } ]
}
```

| 필드 | 의미 |
|------|------|
| `windowSize` | 집계에 쓴 로그 총수(창 상한 이하). |
| `providers` | **항상 4건**(`KAKAO`/`TMAP`/`DATAGOKR`/`SAFEMAP`, 로그 0건도 노출). |
| `providers[].successRate` / `avgDurationMs` | 성공률(0~1) / 평균 소요(ms, 반올림). `total:0` 이면 각각 0. |
| `providers[].p95DurationMs` | 원천 그룹 내 소요시간 95 분위수(ms, nearest-rank·보간 없음). `total:0` 이면 0. |
| `providers[].lastSuccessAtMs` / `lastFailureAtMs` | 마지막 성공/실패 시각(epoch ms, 없으면 null). |
| `providers[].todayCount` / `quotaLimit` / `quotaWarning` | 오늘(Asia/Seoul 자정 이후) 호출 수 / 일일 쿼터 상한(없으면 null) / `todayCount` 가 상한의 80% 이상이면 true. |
| `providers[].quotaBurnPerHour` | 오늘 경과시간 기준 **시간당 쿼터 소진율**(호출/시간). 쿼터 있는 원천(`DATAGOKR`)만, 없으면 null. **근사**: 자정~현재 평균 속도가 하루 끝까지 유지된다는 선형 외삽(버스트·야간 편차 미반영). 자정 직후 경과 0 이면 0. |
| `providers[].quotaProjectedTodayTotal` | 현재 소진율이 유지될 때 오늘 마감 예상 총 호출 수(=`round(quotaBurnPerHour × 24)`). 쿼터 없으면 null. |
| `providers[].hourlyCalls` | 이 원천의 최근 24시간 시간별 호출빈도 — 전체 `hourlyTrend` 와 **같은 경계·같은 버킷**(호출 0건 시간도 노출). |
| `hourlyTrend` | 원천 무관 전체 호출량, 최근 24시간 1시간 버킷(오래된→최신, Asia/Seoul 정각). |

- **정직성**: 모든 값은 실제 접속 내역만으로 계산한다 — 목/샘플/하드코딩 없음. 쿼터 예측은 선형 외삽 **근사**임을 위 표에 명시했다.

---

## `/api/admin/slack-config` — Slack 알림 설정 (관리자)

`/api/admin/*` 는 SecurityConfig 가 `ROLE_ADMIN` 으로 보호한다(Bearer JWT). 운영 알림(Slack)의 **발송 on/off·스로틀·카테고리별 뮤트**를 웹에서 편집한다. 출처: `api/admin/AdminController.kt`, `domain/admin/SlackConfigService.kt`, `support/monitoring/SlackAlertConfig.kt`.

> **보안(webhook 편집 불가)**: webhook URL 은 env 시크릿(`SLACK_WEBHOOK_URL` → `bueongi.alert.slack-webhook-url`)이라 **편집 대상이 아니며**, 조회 시에도 **원문을 절대 노출하지 않고 마스킹만** 돌려준다(`https://hooks.slack.com/services/T***/B***/***` 형태, 형식이 다르면 앞 8자만 남기고 별표). PUT 은 `enabled`/`throttleMinutes`/`mutedCategories` 만 받는다.
> **효과 발송 조건**: 실제 Slack 전송 = `webhookConfigured && enabled`. 뮤트된 카테고리는 **발송만 생략**하고 장애 이력(`GET /api/admin/alerts`)에는 그대로 남는다.

**알림 카테고리(고정 5종)** — key 는 실제 알림 key 의 `:` 앞부분과 일치한다:

| key | label | 실제 알림 key 예 |
|-----|-------|------------------|
| `api-500` | 서버 오류(미처리 500) | `api-500:<예외클래스>` |
| `kakao-place-fallback` | 장소검색 폴백(Kakao) | `kakao-place-fallback` |
| `tmap-route-fallback` | 경로 폴백(Tmap) | `tmap-route-fallback` |
| `safetySpot-index-empty` | 안심시설 데이터 없음 | `safetySpot-index-empty` |
| `safetySpot-refresh` | 시설 라이브 갱신 실패 | `safetySpot-refresh:<cdnType>` |

### `GET /api/admin/slack-config` — 현재 설정 조회

**200 응답** (`SlackConfigResponse`):

```json
{
  "webhookConfigured": true,
  "webhookMasked": "https://hooks.slack.com/services/T***/B***/***",
  "enabled": true,
  "throttleMinutes": 10,
  "categories": [
    { "key": "api-500", "label": "서버 오류(미처리 500)", "muted": false },
    { "key": "kakao-place-fallback", "label": "장소검색 폴백(Kakao)", "muted": false },
    { "key": "tmap-route-fallback", "label": "경로 폴백(Tmap)", "muted": false },
    { "key": "safetySpot-index-empty", "label": "안심시설 데이터 없음", "muted": false },
    { "key": "safetySpot-refresh", "label": "시설 라이브 갱신 실패", "muted": false }
  ]
}
```

- `webhookConfigured`: webhook env 설정 여부. `webhookMasked`: 마스킹 URL(미설정이면 `null`). **원문 비노출.**
- `enabled`: 마스터 발송 토글. `throttleMinutes`: 같은 key 재발송 억제 창(분).
- `categories`: **항상 위 5종 고정 순서**, 각 `muted` 여부.

### `PUT /api/admin/slack-config` — 설정 편집

**요청 본문** (`SlackConfigUpdateRequest`):

```json
{ "enabled": true, "throttleMinutes": 30, "mutedCategories": ["api-500", "tmap-route-fallback"] }
```

- `enabled` (필수): 마스터 발송 토글. 누락 → 400 `VALIDATION_FAILED`.
- `throttleMinutes` (필수): **[1, 1440]**. 범위 밖 → 400 `VALIDATION_FAILED`.
- `mutedCategories` (선택, 기본 `[]`): 뮤트할 카테고리 key 목록. **위 5종 밖 key → 422 `INVALID_SLACK_CATEGORY`**(부분 적용 없음 — 하나라도 알 수 없으면 전체 거부).
- webhook 은 편집 불가(본문에 없음).

**200 응답**: 갱신된 `SlackConfigResponse`(GET 과 동일 shape). PUT 직후 GET 하면 변경이 반영돼 있다.

### `POST /api/admin/slack-config/test` — 테스트 발송

효과 발송 가능(`webhookConfigured && enabled`)이면 **동기**로 테스트 메시지를 1회 발송한다(스로틀·뮤트 무시). 발송 실패·예외·비활성은 모두 `sent:false`(예외를 던지지 않는다).

**요청**: 없음(본문 불요).

**200 응답** (`SlackTestResponse`):

```json
{ "sent": true, "enabled": true }
```

- `sent`: 테스트 발송이 2xx 로 성공했는가.
- `enabled`: 현재 효과 발송 가능 여부(`webhookConfigured && enabled`).

---

## 도메인 상수 한눈에

| 상수 | 값 | 출처 |
|------|----|----|
| `MAX_KEYWORD_LENGTH` | 100 자 | `PlaceSearchService` |
| `MIN_TRIP_DISTANCE_METERS` | 1.0 m | `RouteDtos.kt` |
| `MAX_TRIP_DISTANCE_METERS` | 100_000 m (100km) | `RouteDtos.kt` |
| `MAX_PER_TYPE`(시설) | 8 | `SafetySpotService` |
| 도보 속도 | 67 m/분 (최소 1분) | `Geo.walkMinutes` |
| 거리 표기 경계 | < 1000m → `"m"`, 이상 → `"km"` | `Geo.formatDistance` |
| distanceFactor | safe 1.18 / main 1.05 / fast 0.92 | `RouteType` |
| `MIN_EXPIRES_HOURS` / `MAX_EXPIRES_HOURS` | 1 h / 3 h | `share/ShareDtos.kt` |
| `DEFAULT_EXPIRES_HOURS`(미지정 시) | 1 h | `share/ShareDtos.kt` |
| `MIN_THROTTLE_MINUTES` / `MAX_THROTTLE_MINUTES` | 1 / 1440 분 | `support/monitoring/SlackAlertConfig.kt` |
