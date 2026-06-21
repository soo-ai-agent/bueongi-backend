# cycle38 — 라이브 bootRun share 왕복 HTTP smoke (런타임 검증 산출물)

## 요청
Dorothy 자동 루프(backend 역할) 38회차. consensus.md(37회차)·handoff.md(37회차)·에이전트 정의 복원 후 "다음 한 사이클" 수행. 깨진 빌드/테스트 최우선 수정 + 검증 가능한 산출물 최소 1개.

## 진행
- 상태 복원: consensus.md(37회차)·handoff.md(37회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음. (`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 5 executed. XML 집계 **total tests=150 그린**. 깨진 빌드/테스트 없음 — 37회차와 일치.
- 칸반 점검(`list_tasks` **33건**, 37회차와 동일 건수): 내 영역 `cdb5314d` planned 100% done. bueongi backend 신규 할당 0건. backlog 미완료(`f8aaf6dd`/`5f2bced5`/`bscan-19`/`79e12f6b`/`c7e93835`/`0e823341`) 전부 triplan/soo-auth/bueongi frontend-src — 경계 밖. 소스 변경 0건(37회차 02:15 이후, main 27 / test 23 .kt).
- **점검 각도 전환(38회차 — 22~37회차 16 사이클 연속 정적 교차검증(코드/문서 grep)에서 전환)**: 직전 사이클들은 위험 클래스의 "구조적 부재/봉인"을 정적으로만 확인. 단위/슬라이스(@WebMvcTest 7)/컨텍스트 부팅(BueongiContextLoadTest, 28회차)은 있으나 **실제 `bootRun`으로 살아있는 서버를 띄워 HTTP를 왕복시킨 검증은 38 사이클 동안 한 번도 없었음**. 이번엔 그 갭을 메움 — 실제 직렬화(@JsonProperty snake_case)·라우팅(`/share` prefix)·Bean Validation RFC7807·guardian.html 템플릿 치환·"항상 200 expired" 계약이 **런타임에서 실제로 성립하는가**를 살아있는 서버로 독립 검증.

## 산출물
실제 `./gradlew bootRun` 기동(포트 8119, Redis 미설정 → 인메모리 폴백, 외부 인프라 불요) 후 share 4종 + 경계 3종 **라이브 HTTP smoke 전부 통과(drift 0)**:

| # | 검증 | 결과 |
|---|------|------|
| health | `GET /api/health` | 200 `{status:UP, placeSource:seed, locationStore:memory}` ✓ |
| ① | `POST /share/create {expires_in_hours:2}` | 200 `token`+`share_url={base}/share/{token}`+`expires_at`(+2h) ✓ |
| ② | `POST /share/{token}/location {lat,lng}` | 200 `updated_at`+`expires_at` ✓ |
| ③ | `GET /share/{token}/location` | 200 `{lat:37.5665, lng:126.978, updated_at(snake_case), expired:false}` ✓ |
| ④ | `GET /share/{token}` (guardian.html) | 200, 5683 bytes, **토큰 정확히 1회 치환·`__SHARE_TOKEN__` placeholder 0건 잔존** ✓ |
| ⑤ | `GET /share/BOGUS/location` (없는 토큰) | **항상 200** `{lat:null,lng:null,updated_at:null,expired:true}` — 보호자 만료화면 계약 ✓ |
| ⑥ | `POST /share/BOGUS/location` (없는 토큰) | **404** RFC7807 `code:SHARE_SESSION_NOT_FOUND` ✓ |
| ⑦ | `POST /share/create {expires_in_hours:99}` (범위 1~3 초과) | **400** RFC7807 `code:VALIDATION_FAILED`, `field:expiresInHours, message:"3 이하여야 합니다"` ✓ |

- **정적 검증이 줄 수 없는 신규 보증**: ③의 `updated_at` snake_case 가 **실제 와이어로 직렬화**됨(25회차 회귀 가드의 런타임 짝), ⑦의 @Min/@Max 가 **실제로 RFC7807 트리거**, ⑤의 "항상 200 expired" 가 런타임에서 실제 성립, ④ guardian.html 템플릿 치환이 실서빙에서 동작. `docs/API.md` §`/share` 정본·150 단위 테스트·37회차 정적 계약 정합과 **런타임이 1:1 일치**.
- 서버 정상 종료(포트 8119 잔존 0). **소스/테스트/문서 변경 0건**(순수 런타임 검증).

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 150 그린.
- 라이브 bootRun smoke 7종 전부 기대값 일치(위 표). drift 0.
- escalate/report-issue 미호출(막힘 없음). 시크릿 비노출(KAKAO 키 미설정 seed 모드, 토큰은 1회용 capability 토큰).

## 잔여·인계
1. **38회차 라이브 smoke drift 0 + 37회차 정적 계약 정합 + 22~36회차 보안/동시성/부팅/환경변수 클래스 전부 봉인 — 미고정 갭 0**: 다음 사이클도 베이스라인 그린(150) 확인 후 nothing-to-do 가 정상(억지 마이크로 가드/리팩터/문서 재작성 금지). bueongi backend 12 사이클 연속 신규 작업 0건.
2. **Frontend `78eb6275`(80%) 지금 안전 연동 가능 — 계약이 런타임에서 검증됨**: `/share` 4종. 함정: 경로 `/share`(not `/api`), POST(not PUT), DELETE 없음, GET location 항상 200+`expired`, TTL 1~3h. GET location 응답 = `lat`/`lng`/`updated_at`(snake_case)/`expired`.
3. **신규 backend 태스크 등장 시에만 착수**. 현 backlog 미완료 전부 경계 밖. **라이브 smoke 절차(bootRun→curl 왕복, Redis 불요)는 향후 share 변경 시 재실행 가능한 런타임 회귀 점검으로 활용 가능.**

## 메타
- roleId: backend / engine: claude(opus) / cycle: 38
- 변경 파일: 없음(순수 런타임 검증) / 산출물: 본 보고서 + 라이브 smoke 증거
- 베이스라인: 150 그린 / 칸반: 33건(신규 backend 0)
