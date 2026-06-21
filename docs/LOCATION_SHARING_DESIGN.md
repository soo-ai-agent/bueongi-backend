# bueongi backend — 위치 공유(보호자) 서버 설계 + 구현 노트

> **상태: MVP 계약 4종 + Redis/인메모리 저장소 구현 완료(2026-06-19).**
> 칸반 태스크 `cdb5314d [Backend/Server] Redis 기반 위치 공유 최소 서버 설계/구현`(label `safe-return-mvp`, `role:Backend/Server`)의 산출물.
> 기준 문서는 오케스트레이터 설계 `docs/mvp-orchestrator-design.md` **작업 4(Backend/Server)** — 그 4개 엔드포인트 계약과 Redis 키 모델로 정렬했다.
>
> **이전(18~19회차) 설계와의 차이 — MVP 기준으로 재정렬한 내역**
> | 항목 | 이전(`/api/share/sessions` 설계) | 현재(MVP 작업 4) |
> |---|---|---|
> | 경로 prefix | `/api/share/sessions` | `/share`(보호자 공개 URL 이라 `/api` 와 분리) |
> | 생성 | `POST …/sessions` `{ttlSeconds}` | `POST /share/create` `{expires_in_hours: 1\|2\|3}` |
> | 위치 갱신 | `PUT …/{shareId}/location` | `POST /share/{token}/location` |
> | 조회 | `GET …/{shareId}` → 200/204/404 | `GET /share/{token}/location` → **항상 200 + `expired`** |
> | 보호자 페이지 | (없음) | `GET /share/{token}` → **HTML(Leaflet+OSM, 5초 폴링)** |
> | 종료 | `DELETE …/{shareId}` | (제거 — 자동 만료만, MVP 범위 밖) |
> | TTL | 기본 6h·상한 12h(초 단위) | **1~3시간**(기본 1h) |
> | 위치 stale(90s) | 있음 | (제거 — `expired` 만, MVP 단순화) |
> | 정확도/방향 | `accuracyMeters`/`headingDegrees` | (제거 — `{lat,lng}` 만) |
> | 응답 키 표기 | camelCase | **snake_case**(`share_url`,`expires_at`,`updated_at`) |
> | Redis 구현 | 미착수(인메모리만) | **`RedisLocationStore` 구현**(`share:{token}`/`location:{token}` HASH+TTL) |
>
> 프론트는 아직 백엔드 share API 를 소비하지 않으므로(클라이언트 `navigator.share`/SMS 만) 위 재정렬에 호환성 부담은 없다.
>
> 기존 stateless API 계약은 `docs/API.md`. 이 문서는 그와 **다른 성격(stateful·실시간·TTL)** 의 영역이라 별도로 둔다.

---

## 1. 배경 / 요구

- bueongi 는 안심 귀가 앱. 기존 백엔드는 **stateless·무외부의존**(장소 검색 + 경로/안심시설 비교)(`docs/API.md`).
- 신규 요구(태스크 cdb5314d): 카카오택시 방식 — 사용자가 토큰 URL 을 외부 메신저로 공유하면 **보호자가 로그인 없이 실시간 위치를 5초 폴링으로 조회**하는 최소 서버.
- 기존 도메인과 본질이 다름: 시간에 따라 변하는 라이브 위치(상태 있음) → **만료(TTL) 가능한 저장소(Redis)** 가 적합. Redis 미주입 시 인메모리 폴백.

## 2. 설계 원칙

1. **`/share` prefix + 공통 에러 계약 재사용**: 좌표 오류는 `common/ApiExceptionHandler.kt` 의 RFC 7807 + `code`(400) 그대로. 위치 갱신의 토큰 없음/만료만 404 `SHARE_SESSION_NOT_FOUND`. CORS 는 `/api/**` 정책을 `/share/**` 에도 동일 적용.
2. **PII 최소화 / 비식별 토큰**: 계정·전화번호 대신 추측 불가능한 128-bit URL-safe 랜덤 `token`(22자) 으로만 식별(링크 공유 모델).
3. **자동 만료(TTL)**: 1~3시간 상한 TTL — 공유를 끄지 않아도 시간이 지나면 위치가 사라진다(무기한 추적 금지). 종료(DELETE) API 없이 만료로만 정리.
4. **외부 키 없이 기동(MVP 일관)**: `BUEONGI_REDIS_URL` 미주입(로컬/CI)이면 인메모리 폴백 + `/api/health.locationStore`(`"redis"|"memory"`) 라벨. 보호자 지도도 키 불요(Leaflet+OpenStreetMap).
5. **좌표 규약 동일**: lat ±90·lng ±180, 비유한(NaN/±Inf)·범위 밖 거부.

## 3. API 계약 (구현됨)

base: 사용자 앱 호출은 `http://localhost:8119`. 상세 shape 은 `docs/API.md` 의 `/share` 섹션이 단일 레퍼런스.

| 메서드/경로 | 요청 | 응답 |
|---|---|---|
| `POST /share/create` | `{ expires_in_hours?: 1\|2\|3 }`(생략 시 1h) | `{ token, share_url, expires_at }` |
| `POST /share/{token}/location` | `{ lat, lng }` | `{ updated_at, expires_at }` · 토큰 없음/만료 → 404 |
| `GET /share/{token}/location` | (없음) | 항상 200 `{ lat, lng, updated_at, expired }`(만료/미존재 → `expired:true`+좌표 null, 첫 위치 전 → `expired:false`+좌표 null) |
| `GET /share/{token}` | (없음) | `text/html` 보호자 지도 페이지(Leaflet+OSM, 5초 폴링 JS) |

신규 에러 `code`: `SHARE_SESSION_NOT_FOUND`(404, 위치 갱신 한정). 좌표 오류는 기존 `VALIDATION_FAILED`/`MALFORMED_REQUEST`(400) 재사용.

## 4. 데이터 모델 (Redis)

```text
share:{token}     HASH { created_at, expires_at }   EXPIRE = 상한 TTL(1~3h)
location:{token}  HASH { lat, lng, updated_at }      EXPIRE = 동일 상한 TTL
```

- 상한 만료는 Redis native TTL 이 강제 → `find` 는 `share:{token}` 키 존재만 확인(없으면 만료/미존재). 인메모리는 `find` 시점 lazy 비교로 동치.
- 위치 키도 공유와 **동일 상한 TTL** — 만료 시 위치도 함께 삭제(잔존 추적 금지). PII 미저장(token·좌표·시각만).

## 5. 코드 배치 (구현됨, `io/soo/bueongi/share/`)

```
LocationStore.kt          # 인터페이스(save/find) + ShareSession/StoredLocation 모델
RedisLocationStore.kt     # Redis 구현(share:{token}/location:{token} HASH+TTL)
InMemoryLocationStore.kt  # 폴백(ConcurrentHashMap + lazy 만료)
ShareStoreConfig.kt       # redis-url 유무로 Redis↔인메모리 택1(상호배타 Condition)
ShareDtos.kt              # 요청/응답 + @Valid 좌표 검증 + snake_case(@JsonProperty)
ShareService.kt           # 생성/위치 갱신/조회 + 1~3h TTL + 토큰 발급
ShareController.kt        # 4개 엔드포인트(+ HTML 토큰 주입)
resources/share/guardian.html  # 보호자 지도 페이지(Leaflet+OSM, 5초 폴링)
```
- `BueongiApplication` 은 `RedisAutoConfiguration` 제외(URL 미지정 시 localhost 자동연결 방지). `ShareStoreConfig` 가 URL 주입 시에만 Lettuce 팩토리 생성.
- `ApiExceptionHandler` 에 `SHARE_SESSION_NOT_FOUND` 핸들러, `HealthController.locationStore` 라벨(불리언 파생, 키/주소 미노출).

## 6. 인프라 / 설정 (env)

| env | 기본 | 설명 |
|---|---|---|
| `BUEONGI_REDIS_URL` | (빈 값) | 예 `redis://localhost:6379`. **미설정 시 인메모리 폴백**(MVP·CI 무중단). |
| `BUEONGI_SHARE_BASE_URL` | `http://localhost:8119` | `share_url` 의 base(운영은 공개 도메인). |
| `BUEONGI_CORS_ORIGINS` | `http://localhost:3619` | `/api/**`·`/share/**` 공통 허용 origin(별도 보호자 웹 폴링 시 도메인 추가). |

## 7. 수용 기준 (구현·검증 완료)

1. 생성→위치 POST→GET 으로 같은 좌표 회수(왕복) — 인메모리·Redis(키 계약 모킹) 양쪽. ✅
2. `expires_in_hours` 는 1·2·3 만 허용, 미지정 시 1h. ✅
3. 상한 만료(1~3h) 경과 후 GET → `expired:true`(좌표 null), 위치 POST → 404. ✅
4. 잘못된 좌표(범위 밖/비유한) POST → 400(기존 계약과 동일 code). ✅
5. Redis 미설정 환경에서 전 기능 동작 + `/api/health.locationStore == "memory"`. ✅(스모크 검증)
6. 응답/로그/health 에 PII·Redis 자격증명 미노출. ✅
7. 보호자 HTML 이 키 없이(OSM) 렌더되고 토큰이 주입된다. ✅

## 8. Redis 미구성 시 동작 정책

- `BUEONGI_REDIS_URL` 이 비어있으면 `ShareStoreConfig` 의 `RedisNotConfigured` 조건이 매칭되어 `InMemoryLocationStore`(단일 인스턴스, 재시작 시 휘발) 가 동일 `LocationStore` 계약으로 동작한다. 외부 인프라·키 없이 전 기능·전 테스트가 그린.
- 운영에서 Redis 를 켜려면 `BUEONGI_REDIS_URL` 주입만으로 `RedisLocationStore`(native TTL) 로 무코드변경 전환된다. 다중 인스턴스·재시작 내구성이 필요하면 Redis 를 권장.

## 9. 범위 밖(v2)

보호자 회원가입·페어링·푸시 알림·서버 발송 메시지·경로 이탈 자동감지·WebSocket/SSE 푸시(폴링으로 충분). 위치 정확도/방향 필드, 명시적 종료(DELETE)도 MVP 에서 제외.
