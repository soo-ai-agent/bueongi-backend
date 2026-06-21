# backend 30회차 — nothing-to-do (InMemoryLocationStore 동시성 안전성 내 경계 교차검증)

- 날짜: 2026-06-20 (KST)
- 엔진: Claude(opus, backend)
- 사이클 결과: **nothing-to-do** (코드/테스트/문서 변경 0건 — 순수 검증)

## 요청
- Dorothy 자동 루프 backend 역할 "다음 한 사이클". 깨진 빌드/테스트 우선 수정, 최소 1개 검증 가능 산출물.

## 진행
- 상태 복원: `consensus.md`(29회차)·`handoff.md`(29회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음.
  (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` **BUILD SUCCESSFUL, tests=150/0/0/0**(클린 5 executed). 깨진 빌드/테스트 없음 — 29회차와 일치.
  - 23 suite 집계 합 = 150(share: InMemory 5 / Redis 6 / Controller 14 / Service 12 / StoreConfig 5 등).
- 칸반 점검(`list_tasks` **28건**, 29회차와 동일): 내 영역 `cdb5314d` planned **100% done**. bueongi backend 신규 할당 0건.
  - `556ffb65`(`/v1/` torn-classpath 500)=backlog/triplan, `6a85b153`(QG-2)=done/triplan core-api — 둘 다 경계 밖.
  - FE `3842fabd`·`78eb6275` 80% 진행. 소스 mtime 전부 06-19(직전 사이클 이후 변경 0건).
- **점검 각도(신규 — 30회차까지 미점검)**: 실시간 폴링 앱의 핵심인 **InMemoryLocationStore 동시성 안전성**(POST 위치 갱신 ↔ GET 조회/lazy 만료 제거 병행 시 race)을 코드로 독립 검증. 진짜 race가 있으면 실 버그 수정, 없으면 nothing-to-do.

## 산출물
- **코드/테스트/문서 변경 0건.** 산출물 = 동시성 교차검증 due-diligence(14·17·26~29회차와 동일 패턴) + 본 보고서 + 메모리 베이턴 갱신.

### 검증 결론: InMemoryLocationStore 동시성 안전 (drift 0, 갭 0)
1. **저장소 자료구조**: `ConcurrentHashMap<String, ShareSession>`. `save`=원자적 `put`, `find`=원자적 `get`.
2. **lazy 만료 제거의 동시성 안전(핵심)**: `find`(`InMemoryLocationStore.kt:30`)이 만료 시 `sessions.remove(token, session)` — 단순 `remove(token)`이 아닌 **2-arg CAS(값 조건부)**. "만료 판정 ~ 제거" 사이에 더 새 세션이 들어와도 그 값이 다르면 지우지 않음(코드 주석 `:29` 명시). Redis EXPIRE 와 동치.
3. **위치 갱신 race**: `ShareService.updateLocation`(`:45-51`)은 `find→session.copy(location=...)→save`. 동시 갱신은 last-write-wins=**항상 최신 위치**(올바른 의미 — 위치는 병합 대상 아님). 세션 자체 유실 없음.
4. **CAS 보호 시나리오는 현재 도달 불가**: `updateLocation`의 `copy`가 `expiresAt`를 보존(연장 안 함)하고, `create`는 매번 128-bit 랜덤 token 생성(충돌 사실상 불가) → **같은 token에 "더 새로운 비만료 세션"이 생기는 경로가 없음**. 즉 line 30 CAS가 막는 race는 현 코드 경로에서 발생 불가. 따라서 단순 `remove(token)`도 모든 도달 가능 상태에서 동일 거동.
5. **그래서 왜 가드 테스트를 추가 안 했나**: 그 분기를 직접 때리려면 같은 token에 서로 다른 두 세션을 인위 주입해야 하는데(도달 불가 시나리오·내부 구현 디테일), 이는 consensus가 명시적으로 금지한 **억지 마이크로 가드(busywork)**. 기존 `InMemoryLocationStoreTest` 5건이 저장/조회 왕복·lazy 만료 제거(시계 되감기로 재생성 안 됨 확인 `:46-48`)·덮어쓰기를 이미 결정론 Clock 으로 고정.

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, tests=150/0/0/0(클린). 소스/테스트/문서 무변경이므로 그린 유지.
- escalate/report-issue 미호출(새 막힘·버그 없음 — 동시성 안전 확인). 시크릿 비노출(토큰 값 미기재).

## 잔여·인계
1. **동시성(30) + rate-limit(29) + 부팅 무결성(28) + 500 정보노출(27) + share 계약(22~26) 모두 미고정 갭 0**: 다음 backend 사이클도 베이스라인 그린(150) 확인 후 **nothing-to-do 가 정상**(억지 마이크로 가드/리팩터/문서 재작성 금지).
2. **Frontend `78eb6275`(보호자 웹 지도) 지금 연동 가능**: `docs/API.md` §`/share` 4종. 함정: 경로 `/share`(not `/api`), POST(not PUT), DELETE 없음, 조회 항상 200+`expired`, TTL 1~3h. GET location 응답 = `lat`/`lng`/`updated_at`(snake_case)/`expired`.
3. **`556ffb65`·`6a85b153` 는 triplan(`/v1/`·core-api) — bueongi 아님**. bueongi backend 신규 태스크 등장 시에만 착수. 칸반·베이스라인 테스트 수·신규 소스↔테스트 매핑은 매 사이클 코드로 교차 확인.
