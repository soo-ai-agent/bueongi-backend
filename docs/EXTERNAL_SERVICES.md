# 외부 서비스 — 언제, 무엇이, 어떻게 불리나

처음 보는 사람을 위한 지도다. 이 서버가 바깥과 주고받는 모든 접점을,
**켜질 때 → 켜진 뒤 백그라운드 → 요청이 올 때** 세 시점으로 나눠 적는다.

> 원천별 상세(인증·쿼터·필드)는 [`../../docs/external-api-catalog.md`](../../docs/external-api-catalog.md),
> 위치 공유 설계는 [`LOCATION_SHARING_DESIGN.md`](LOCATION_SHARING_DESIGN.md).

## 한눈 요약

**켜질 때 외부 API 호출은 없다.** 기동 중에 하는 일은 로컬 파일 읽기와 (설정했을 때만) Redis 연결뿐이다.
외부 API(Tmap·Kakao·Slack)는 전부 **사용자 요청이나 사건이 있어야** 불린다.

```
켜질 때        CDN 파일 적재(로컬) · Redis 연결(선택)          ← 외부 API 없음
켜진 뒤        도착 감시 루프(60초마다, 내부 데이터만 훑음)
요청이 올 때   Tmap(경로) · Kakao(장소검색)
사건이 날 때   Slack(운영 알림)
```

## 1. 켜질 때 — 기동 타임라인

```
main() 시작
  │
  ├─ ① 설정 검문      yml + 환경변수 바인딩. 값이 틀리면 여기서 죽는다(fail-fast).
  │                    예: 관리자 비밀번호가 bcrypt 형식이 아니면 APPLICATION FAILED TO START
  │
  ├─ ② 빈 조립
  │     ├─ 안심시설 적재   BUEONGI_FACILITY_DATA_DIR(기본 ../cdn)의 JSON/CSV 를 읽어
  │     │                  메모리 인덱스 구성. **로컬 파일이다 — 네트워크 아님.**
  │     │                  디렉터리가 없으면 경고 로그 후 빈 적재로 계속 뜬다.
  │     └─ Redis 연결      BUEONGI_REDIS_URL 있을 때만. 없으면 인메모리 폴백이라 연결 자체가 없다.
  │
  ├─ ③ 8119 포트 오픈 → "Started" 로그
  │
  └─ ④ 백그라운드 시작    도착 감시 루프(아래 2절)
```

과거에는 기동 1회 + 매월 cron 으로 공공데이터(data.go.kr·safemap)를 라이브 갱신했지만,
**지금은 없다**(커밋 `a0c272f` 에서 클라이언트 모듈 2종 제거). 시설 데이터는 파이프라인이 만든
CDN 파일로만 들어온다. CLAUDE.md 이력에 남은 라이브 갱신 서술은 과거 기록이다.

## 2. 켜진 뒤 — 백그라운드

| 무엇 | 주기 | 바깥과의 접점 |
|---|---|---|
| `MissedArrivalWatchdog` | 60초(설정 가능) | 없음 — 내부 공유 세션만 훑는다. **놓친 도착을 발견하면** 사건을 발행하고, 그 사건을 받은 쪽이 Slack 을 부른다(3절) |

## 3. 요청·사건이 있을 때 — 외부 서비스별

### Tmap (보행 경로) — `clients:client-tmap`

- **언제**: 사용자가 경로 비교를 요청할 때만 (`/api/routes`).
- **안 되면**: 직선 기반 폴백 경로로 응답하고(안전 점수·마커 없음), Slack 에 "전면 실패" 사건을 낸다.
  서버는 죽지 않는다.
- 응답 본문에 NUL(0x00)이 섞여 오는 실측 사례가 있어 문자열로 받아 다듬은 뒤 파싱한다
  (`TmapResponseText.sanitizeTmapJson`).

### Kakao Local (장소 검색) — `clients:client-kakao`

- **언제**: 장소 검색 요청일 때만 (`/api/places?...`). `KAKAO_REST_API_KEY` 미설정이면 아예 안 부르고
  내장 시드 카탈로그로 검색한다.
- **안 되면**: 시드 폴백으로 응답 + Slack 사건. 검색 불능보다 근사 결과가 안전하다는 설계다.

### Slack (운영 알림) — `support:monitoring`

- **언제**: 사건이 났을 때만. 도메인은 Slack 을 모른다 — 사건(`OperationEvent`)을 발행하면
  `core-ops` 리스너가 받아 `SlackAlertNotifier` 로 보낸다.
- 발송 지점: 처리 못한 500 · Tmap 전면 실패 · Kakao 검색 실패 · 시설 적재 0건 · 도착 미확인.
- 같은 사건 key 는 10분 창 안에서 1번만 보낸다(스로틀). `SLACK_WEBHOOK_URL` 미설정이면 전체 무발송(무해).

### Redis (위치 공유 저장) — `storage:db-core`

- **언제**: 켜질 때 연결(설정 시), 이후 공유 세션 읽기/쓰기마다.
- **안 되면**: 미설정 시 인메모리 폴백 — 단일 인스턴스·재시작 시 휘발이지만 계약은 동일.
- 외부 "API" 라기보다 우리 인프라다. 여기 적는 이유는 켜질 때 바깥에 접속하는 유일한 것이라서다.

## 4. 지금 상태를 확인하는 법

```bash
curl -s localhost:8119/api/health
# {"status":"UP","service":"bueongi-backend","placeSource":"live|seed","locationStore":"redis|in-memory"}
```

- `placeSource: live` = Kakao 키가 주입돼 실검색 / `seed` = 내장 카탈로그.
- `locationStore: redis` = Redis 연결됨 / `in-memory` = 폴백.

## 5. 요약 표

| 서비스 | 켜질 때 | 부르는 조건 | 실패 시 |
|---|---|---|---|
| CDN 파일(시설) | **읽음(로컬)** | 기동 1회 | 경고 로그 + 빈 적재로 기동 |
| Redis | 연결(설정 시) | 공유 세션 I/O | 인메모리 폴백 |
| Tmap | 안 부름 | 경로 요청 | 폴백 경로 + Slack |
| Kakao | 안 부름 | 장소 검색 요청 | 시드 폴백 + Slack |
| Slack | 안 부름 | 사건 발생 | 로그만 (알림 유실) |
