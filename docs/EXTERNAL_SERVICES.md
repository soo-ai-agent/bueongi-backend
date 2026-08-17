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

### 전체 구조도

```mermaid
flowchart LR
    subgraph 사용자
        FE[웹·모바일 앱]
    end
    subgraph 백엔드["백엔드 (8119)"]
        RC[route 도메인]
        PC[place 도메인]
        SH[share 도메인]
        OPS[ops 도메인<br/>사건 리스너]
        TM[client-tmap]
        KK[client-kakao]
        SN[SlackAlertNotifier]
    end
    subgraph 외부
        TMAP[Tmap API]
        KAKAO[Kakao Local]
        SLACK[Slack 웹훅]
        REDIS[(Redis<br/>선택)]
    end
    CDN[/CDN 파일<br/>로컬 디스크/]

    FE -->|경로 요청| RC --> TM -->|POST| TMAP
    FE -->|장소 검색| PC --> KK -->|GET| KAKAO
    FE -->|위치 공유| SH --> REDIS
    RC -. 폴백 사건 .-> OPS
    PC -. 폴백 사건 .-> OPS
    OPS --> SN -->|비동기 POST| SLACK
    CDN -->|켜질 때 1회 적재| 백엔드
```

- **실선** = 요청이 있을 때만 흐르는 호출 · **점선** = 실패했을 때만 나는 사건.
- 켜질 때 바깥으로 나가는 것은 Redis 연결(선택)뿐이고, CDN 파일은 로컬 디스크다.

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
**지금도 자동으로는 안 한다.** 시설 데이터는 파이프라인이 만든 CDN 파일로 들어온다.
어댑터(client-datagokr·client-safemap)는 2026-08-17 복원됐지만 **아무도 부르지 않는다** —
스케줄·갱신 서비스를 붙이는 것은 쿼터 결정(카탈로그 참고)이 필요한 별도 작업이다. CLAUDE.md 이력에 남은 라이브 갱신 서술은 과거 기록이다.

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

### Slack (운영 알림) — `clients:client-slack`

- **언제**: 사건이 났을 때만. 도메인은 Slack 을 모른다 — 사건(`OperationEvent`)을 발행하면
  `core-ops` 리스너가 받아 `SlackAlertNotifier` 로 보낸다.
- 발송 지점: 처리 못한 500 · Tmap 전면 실패 · Kakao 검색 실패 · 시설 적재 0건 · 도착 미확인.
- 같은 사건 key 는 10분 창 안에서 1번만 보낸다(스로틀). `SLACK_WEBHOOK_URL` 미설정이면 전체 무발송(무해).

### Redis (위치 공유 저장) — `storage:db-core`

- **언제**: 켜질 때 연결(설정 시), 이후 공유 세션 읽기/쓰기마다.
- **안 되면**: 미설정 시 인메모리 폴백 — 단일 인스턴스·재시작 시 휘발이지만 계약은 동일.
- 외부 "API" 라기보다 우리 인프라다. 여기 적는 이유는 켜질 때 바깥에 접속하는 유일한 것이라서다.

## 4. 한 호출의 해부 — 코드가 실제로 하는 일

### 공통 기반 — 모든 외부 호출이 딛고 서는 것

```
스프링 자동구성 RestClient.Builder ──┬─→ TmapRouteClient   (clone().build())
   ↑ 전역 타임아웃이 여기 걸린다      ├─→ KakaoPlaceClient  (clone().build())
                                     └─→ SlackAlertNotifier (clone().build())
```

- **타임아웃은 yml 한 곳에서 전부 적용된다** (`application.yml`): 연결 3초, 응답 10초.
  미설정이면 무한대라, 외부가 느려지는 "슬로우 장애"에서 우리 스레드가 전부 잡혀 전체 장애로 번진다.
- **키는 생성자에서 주입**받고(`@Value`), 키가 비어 있으면 `isEnabled = false` — **호출 자체를 안 한다.**
- **통신 응답 모양(JSON 구조)은 clients 모듈 밖으로 안 나간다.** 클라이언트가 우리 말(`TmapRoute`·`KakaoPlace`)로
  번역해서 내보낸다. 외부 규격이 바뀌어도 고칠 곳은 그 모듈 하나다.

### Tmap 경로 — 요청 한 번의 여정

사용자가 경로 비교를 누르면:

```mermaid
sequenceDiagram
    participant S as RouteService
    participant C as TmapRouteClient
    participant Cache as 캐시(10분)
    participant T as Tmap API

    S->>C: fetchRoutes(출발, 도착)
    loop 옵션 3종 (추천·큰길·최단)
        C->>Cache: 같은 요청 있었나?
        alt 캐시 적중
            Cache-->>C: TmapRoute (네트워크 생략)
        else 미적중
            C->>T: POST /routes/pedestrian (appKey)
            T-->>C: JSON (NUL 섞일 수 있음)
            C->>C: 문자열로 받아 NUL 제거 후 파싱·번역
            C->>Cache: 저장
        end
    end
    alt 하나라도 성공
        C-->>S: List<TmapRoute>
    else 전부 실패
        C-->>S: 빈 목록 (예외 안 던짐)
        S->>S: 직선 폴백 경로 생성 + 사건 발행 → Slack
    end
```


```
RouteService ─→ TmapRouteClient.fetchRoutes(출발, 도착)
                  │  옵션 3종("추천 0"·"큰길 4"·"최단 10")을 하나씩 → 최대 3회 POST
                  ▼
    ① 캐시 확인      같은 출발·도착·옵션이 10분 안에 있으면 네트워크 생략
    ② POST 요청      https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1
                     헤더 appKey — 로그·예외에는 절대 안 실림
    ③ 문자열로 수신   ★ 바로 객체로 안 받는다. Tmap 이 본문에 NUL(0x00)을 섞어 보내는
                     실측 사례가 있어, sanitizeTmapJson 으로 다듬은 뒤 파싱한다
    ④ 번역           GeoJSON → TmapRoute (거리·시간·좌표열·안내점)
    ⑤ 실패하면       예외를 위로 안 던진다 — null 반환 + 원인 사슬 로그
                     ("A: msg <- B: msg" — 겉 메시지만 남기면 진짜 원인이 사라진 사건의 교훈)
```

세 옵션이 **전부** null 이면 route 도메인이 **직선 기반 폴백 경로**를 만들어 응답하고
(안전 점수·마커 없음), 사건 `tmap-route-fallback` 을 발행한다 → Slack 알림.
**사용자 응답은 어떤 경우에도 나간다** — 외부 장애가 우리 장애가 되지 않게.

### Kakao 장소 검색 — 게이트 → 호출 → 폴백

```
PlaceSearchService.search(키워드)
    │
    ├─ 키 없음(isEnabled=false)  ─→ 내장 시드 카탈로그 검색   ← 호출 자체가 없다
    │
    └─ 키 있음 ─→ GET dapi.kakao.com/v2/local/search/keyword.json?query=…&size=15
                   헤더 Authorization: KakaoAK <키>
                   │
                   ├─ 성공 → 응답 문서를 KakaoPlace 로 번역해 반환
                   └─ 예외 → catch → 시드 폴백 + 사건 kakao-place-fallback → Slack
```

폴백을 두는 이유: 안심 앱에서 "검색 불능"보다 "근사 결과"가 안전하다는 설계 결정이다.

### Slack 알림 — 도메인은 Slack 을 모른다

```mermaid
sequenceDiagram
    participant D as route 도메인
    participant P as RouteEventPublisher
    participant E as 스프링 이벤트 버스
    participant O as core-ops 리스너
    participant N as SlackAlertNotifier
    participant W as Slack 웹훅

    D->>P: safeRouteFellBack("...")  (Slack 이란 말이 없다)
    P->>E: OperationEvent(key, title, detail)
    E->>O: @EventListener 수신
    O->>N: notify(key, title, detail)
    N->>N: 이력 기록 → 활성·뮤트 확인 → 스로틀(10분 1회)
    N--)W: 비동기 POST (전용 스레드)
    Note over N,W: 실패해도 로그만 — 앱 무영향
```

```
도메인 코드          route 도메인: routeEvents.safeRouteFellBack("…")     ← Slack 이란 말이 없다
    │                (도메인마다 발행 전용 서비스 XxxEventPublisher)
    ▼
스프링 이벤트        OperationEvent(key, title, detail) 발행
    ▼
core-ops 리스너      받아서 AlertNotifier.notify(key, …) 호출
    ▼
SlackAlertNotifier   ① 이력 기록(발송 여부와 무관하게 남김)
                     ② 웹훅 미설정·뮤트 카테고리면 여기서 끝(무해)
                     ③ 같은 key 는 10분에 1번만(스로틀) — 장애 중 알림 폭주 방지
                     ④ 전용 단일 스레드로 비동기 발송 — 사용자 요청 스레드를 안 막는다
                     ⑤ 발송 실패해도 로그만 남기고 앱은 무영향
```

### 설계 원칙 한 줄 요약

| 원칙 | 코드에서 |
|---|---|
| 키 없으면 안 부른다 | `isEnabled` 게이트 |
| 실패를 위로 안 던진다 | null/폴백 + 사건 발행 |
| 느린 외부에 안 끌려간다 | 전역 타임아웃 3s/10s |
| 통신 모양은 모듈 안에 | 클라이언트가 우리 말로 번역 |
| 원인을 잃지 않는다 | cause 사슬 로그 |
| 시크릿은 어디에도 | appKey·웹훅 URL 비로그 |

## 5. 지금 상태를 확인하는 법

```bash
curl -s localhost:8119/api/health
# {"status":"UP","service":"bueongi-backend","placeSource":"live|seed","locationStore":"redis|memory"}
```

- `placeSource: live` = Kakao 키가 주입돼 실검색 / `seed` = 내장 카탈로그.
- `locationStore: redis` = Redis 연결됨 / `memory` = 인메모리 폴백.

## 6. 요약 표

| 서비스 | 켜질 때 | 부르는 조건 | 실패 시 |
|---|---|---|---|
| CDN 파일(시설) | **읽음(로컬)** | 기동 1회 | 경고 로그 + 빈 적재로 기동 |
| Redis | 연결(설정 시) | 공유 세션 I/O | 인메모리 폴백 |
| Tmap | 안 부름 | 경로 요청 | 폴백 경로 + Slack |
| Kakao | 안 부름 | 장소 검색 요청 | 시드 폴백 + Slack |
| Slack | 안 부름 | 사건 발생 | 로그만 (알림 유실) |
