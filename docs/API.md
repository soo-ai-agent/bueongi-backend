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
| `ORIGIN_REQUIRED` | 422 | Origin Required | ❌ | compare/facilities 에서 `origin` 키 누락(위치 권한 거부/미지원). **에러 토스트 아님** → 위치 권한 재요청 또는 "출발지 직접 선택" UI 유도. |
| `SAME_ORIGIN_DESTINATION` | 422 | Same Origin And Destination | ✅ `destination`(rejectedValue=직선거리m) | 출발-도착 직선거리 < 1m(사실상 같은 지점). 도착지 재선택 유도. |
| `TRIP_TOO_FAR` | 422 | Trip Too Far | ✅ `destination`(rejectedValue=직선거리m) | 출발-도착 직선거리 > 100km(도착지 오선택/위경도 뒤바뀜 추정). 도착지 재선택 유도("100km 이내" 안내). |
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
{ "status": "UP", "service": "bueongi-backend", "placeSource": "seed", "locationStore": "memory" }
```

- `status`: 항상 `"UP"`(요청 처리 가능하면 도달).
- `service`: `"bueongi-backend"`.
- `placeSource`: `"live"`=외부 실연동(Kakao 키 주입됨) / `"seed"`=내장 시드 폴백. **키 설정 여부 불리언에서 파생한 라벨일 뿐 키 값은 절대 노출하지 않는다.** ops/오케스트레이터가 키 배선/폴백을 검증하는 용도.
- `locationStore`: `"redis"`=Redis 위치공유 저장소 / `"memory"`=인메모리 폴백(`BUEONGI_REDIS_URL` 미주입 시 MVP 기본). **저장소 종류 라벨일 뿐 Redis URL·자격증명은 절대 노출하지 않는다.** (위치 공유 섹션 참조)

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
- `lat`/`lng`: `POST /api/routes/compare` 의 `destination` 좌표로 바로 사용 가능(additive, 무시해도 무방).

**동작/정책**:

- 매칭: 이름·주소 부분일치, 앞뒤 트림 + 라틴 대소문자 무시.
- 트림 후 **빈 검색어 → 빈 목록 `[]`**(에러 아님 — 프론트는 '최근 검색'으로 폴백). 추측 결과를 만들지 않는다.
- 트림 후 **100자 초과 → 400 `INVALID_KEYWORD`**(외부 API 호출 전에 차단). 경계(정확히 100자)는 허용. (`MAX_KEYWORD_LENGTH=100`)
- 키 주입 시 Kakao Local 실검색 시도 → **실패 시 시드 폴백**(안심앱: 검색 불능보다 근사 결과가 안전). 키 없으면 시드. 어느 경로든 `PlaceItem` 계약 동일.

---

## `POST /api/routes/compare` — 경로 비교

출발/도착 좌표로 safe/main/fast 3개 경로를 만든다. 출처: `routes/RouteController.kt`, `routes/RouteService.kt`, `routes/RouteType.kt`, `routes/RouteDtos.kt`.

**요청 본문** (`RouteRequest`):

```json
{
  "origin": { "lat": 37.4979, "lng": 127.0276 },
  "destination": { "lat": 37.5563, "lng": 126.9236, "name": "홍대입구역" },
  "routeType": "safe"
}
```

- `origin` (`{lat,lng}`): **누락 가능**(위치 권한 거부/미지원). 누락 → 422 `ORIGIN_REQUIRED`. 값이 있으면 좌표 범위 검증(범위 초과 → 400 `VALIDATION_FAILED`).
- `destination` (`{lat,lng,name?}`): **필수**(누락 → 400 `VALIDATION_FAILED`, `destination` @NotNull). `name` 은 optional 표시명.
- `routeType` (optional): compare 에서는 **무시됨**(항상 3개 경로 반환). facilities 전용.

**200 응답** (`List<RouteOption>`, 항상 safe→main→fast 순):

```json
[
  {
    "id": "1", "name": "추천 경로", "time": "24분", "dist": "1.2km",
    "desc": "가장 밝고 안심 시설이 잘 갖춰진 길입니다.",
    "tags": [ { "text": "CCTV 많음", "variant": "mint" }, { "text": "밝은 길", "variant": "yellow" } ],
    "type": "safe"
  }
]
```

| 필드 | 의미 |
|------|------|
| `id` | safe="1" / main="2" / fast="3" |
| `name` | safe="추천 경로" / main="주요 도로" / fast="최단 경로" |
| `time` | `"N분"`. 도보 67m/분(≈4km/h), 최소 1분. |
| `dist` | 1km 미만 `"850m"`(10m 단위 반올림), 이상 `"1.2km"`(소수1자리). |
| `desc` | 유형 설명 문구. |
| `tags` | `{text, variant}` 목록. `variant`: `default`/`mint`/`blue`/`yellow`/`outline`(프론트 Badge variant → 아이콘 매핑). |
| `type` | `safe`/`main`/`fast`. |

- 거리 계산: 출발-도착 직선거리(haversine) × 유형 `distanceFactor`(safe 1.18 / main 1.05 / fast 0.92). 추천(safe)이 가장 길지만 가장 안전.
- **가드(순서 고정, 공통)**: `ORIGIN_REQUIRED`(422) → `SAME_ORIGIN_DESTINATION`(422, 직선거리<1m) → `TRIP_TOO_FAR`(422, 직선거리>100km). (`RouteRequest.validatedOrigin()`)

---

## `POST /api/routes/facilities` — 경로상 안심시설

선택 경로(routeType) 위의 안심시설 POI + 집계. 출처: `routes/RouteController.kt`, `routes/FacilityService.kt`, `routes/FacilityDtos.kt`.

**요청 본문** (`RouteRequest`, compare 와 동일):

```json
{
  "origin": { "lat": 37.4979, "lng": 127.0276 },
  "destination": { "lat": 37.5563, "lng": 126.9236 },
  "routeType": "safe"
}
```

- `routeType` (optional): `safe`/`main`/`fast`(또는 id `1`/`2`/`3`). **null/미상 → safe**(안전 우선 기본).
- `origin`/`destination` 규약·가드는 compare 와 동일.

**200 응답** (`FacilitiesResponse`):

```json
{
  "pois": [
    { "type": "start",  "x": 12, "y": 84, "lat": 37.4979, "lng": 127.0276 },
    { "type": "cctv",   "x": 22, "y": 73, "lat": 37.50,   "lng": 127.01 },
    { "type": "end",    "x": 88, "y": 16, "lat": 37.5563, "lng": 126.9236 }
  ],
  "summary": { "cctv": 6, "bell": 3, "store": 2, "police": 1, "total": 12 }
}
```

**`pois[]`** (`FacilityPoi`):

| 필드 | 의미 |
|------|------|
| `type` | `start`/`end`/`cctv`/`bell`/`store`/`police`. (`start`/`end` 는 summary 집계 제외) |
| `x`,`y` | 정규화 표시 좌표(0~100). 좌하단(출발)→우상단(도착) 대각선 + 유형별 측면 분리. |
| `lat`,`lng` | 실 지리 좌표. **출발-도착 직선 경로 위**에 결정론적 배치(선형 보간). |
| `name` | optional. |

**`summary`** (`FacilitySummary`): `cctv`/`bell`/`store`/`police` 와 `total`. **생성된 POI 를 실제로 집계한 값**이라 화면 표시와 항상 일치(없는 데이터를 지어내지 않음). `total = cctv + bell + store + police`(start/end 제외).

**시설 개수 도메인 불변식**:

- 개수 = `직선거리 × distanceFactor(km) × 유형별 km당 밀도`, 반올림, **유형별 상한 8**.
- km당 밀도(safe/main/fast): cctv 6/4/2, bell 3/1.5/0.5, store 2/4/1.5, police 1/0.5/0.
- **per-km 안심시설 밀도(cctv·bell·police)는 SAFE > MAIN > FAST 로 엄격히 단조 감소**(추천 경로가 가장 안전). store(편의점)만 예외로 MAIN 최대(상업/유동인구).
- **SAFE(추천) 최소 보정**: 짧은 경로라도 cctv≥2, bell≥1, store≥1 보장(추천 경로가 '시설 0'으로 보이지 않게). SAFE 에만 적용.

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
{ "expires_in_hours": 2 }
```

- `expires_in_hours` (optional): 만료까지의 시간(시). **1·2·3 만 허용**(범위 밖 → 400 `VALIDATION_FAILED`). 생략(빈 POST 포함) → 1시간(`DEFAULT_EXPIRES_HOURS`, 가장 보수적).

**200 응답** (`CreateShareResponse`):

```json
{
  "token": "qZ3kP...(128-bit URL-safe 토큰)",
  "share_url": "http://localhost:8119/share/qZ3kP...",
  "expires_at": "2026-06-19T14:00:00Z"
}
```

- `share_url`: 앱이 OS 공유 시트로 넘겨 사용자가 카카오톡/문자 등으로 직접 공유. base 는 `BUEONGI_SHARE_BASE_URL`(운영은 공개 도메인).

### `POST /share/{token}/location` — 위치 갱신 (사용자 단말, 5초마다)

**요청 본문** (`UpdateLocationRequest`):

```json
{ "lat": 37.4979, "lng": 127.0276 }
```

- `lat`/`lng`: **필수**. 좌표 규약(lat ±90·lng ±180) + 비유한(`NaN`/±`Infinity`) 거부. 범위 위반 → 400 `VALIDATION_FAILED`, 비숫자 리터럴 → 400 `MALFORMED_REQUEST`.
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

### `GET /share/{token}` — 보호자 지도 페이지 (HTML)

**200** `text/html`. 지도(Leaflet + OpenStreetMap 타일, **지도 SDK 키 불요**) + 5초 폴링 JS 를 담은 보호자 페이지. 토큰 유효성과 무관하게 페이지를 주고, 폴링 JS 가 위 `GET location` 의 `expired` 로 만료/대기/라이브 상태를 그린다. 정적 리소스: `resources/share/guardian.html`.

---

## 도메인 상수 한눈에

| 상수 | 값 | 출처 |
|------|----|----|
| `MAX_KEYWORD_LENGTH` | 100 자 | `PlaceSearchService` |
| `MIN_TRIP_DISTANCE_METERS` | 1.0 m | `RouteDtos.kt` |
| `MAX_TRIP_DISTANCE_METERS` | 100_000 m (100km) | `RouteDtos.kt` |
| `MAX_PER_TYPE`(시설) | 8 | `FacilityService` |
| 도보 속도 | 67 m/분 (최소 1분) | `Geo.walkMinutes` |
| 거리 표기 경계 | < 1000m → `"m"`, 이상 → `"km"` | `Geo.formatDistance` |
| distanceFactor | safe 1.18 / main 1.05 / fast 0.92 | `RouteType` |
| `MIN_EXPIRES_HOURS` / `MAX_EXPIRES_HOURS` | 1 h / 3 h | `share/ShareDtos.kt` |
| `DEFAULT_EXPIRES_HOURS`(미지정 시) | 1 h | `share/ShareDtos.kt` |
