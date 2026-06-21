# [backend] 27회차 사이클 보고서 — 신규 triplan 보안발굴(QG-2) 내 경계 교차검증 (nothing-to-do)

## 요청
Dorothy 자동 루프(backend 역할) 1사이클. consensus.md(26회차)·handoff.md(26회차)·에이전트 정의 복원 후
다음 한 사이클을 결정적으로 수행. 깨진 빌드/테스트 최우선, 최소 1개 검증가능 산출물.

## 진행
- 상태 복원: consensus.md(26회차)·handoff.md(26회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음.
  (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` **BUILD SUCCESSFUL, tests=150/0/0/0**(클린 5 executed). 깨진 빌드/테스트 없음 — 26회차와 일치.
- 칸반 점검(`list_tasks` **28건**, 27→+1): 내 영역 `cdb5314d` planned **100% done**. bueongi backend 신규 할당 0건.
  신규 등장 2건은 **둘 다 `Project: .../triplan`**(경계 밖):
  - `556ffb65`(라이브 bootRun JVM torn-classpath `/v1/` 500) — triplan `/v1/`(bueongi 는 `/api/`).
  - `6a85b153`(QG-2 sanitize 500 catch-all 회귀 가드 갭) — triplan core-api `ApiControllerAdvice`.
- **점검 각도(17회차 패턴 — 활성 형제-프로젝트 보안 발굴을 내 경계에 교차 적용)**: 오늘(06-19 11:23) 신규 등록된
  triplan 보안 태스크 `6a85b153` 이 제기한 위험 클래스 = "500 catch-all `handleException` 이 `e.message`(내부 FQN/
  DB에러/서드파티 메시지)를 응답 detail 로 운반하는데 전용 회귀 가드 부재". **같은 클래스가 bueongi 백엔드에도
  있는가?** 를 코드로 독립 검증.

## 산출물
**코드/테스트/문서 변경 0건(순수 검증).** 산출물 = 활성 보안발굴 교차검증 결과(due-diligence).

검증 결과 — **bueongi 는 QG-2 클래스에 이미 안전 + 이미 회귀 가드까지 봉인됨**:
1. **소스(`common/ApiExceptionHandler.kt:220-230` `handleUncaught`)**: detail 에 **고정 상수**
   (`"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."`)만 싣고, `ex.message`/스택트레이스는
   `log.error`(서버 로그)로만 보냄. triplan 의 `respond(DEFAULT_ERROR, detail=e.message)` 누출 패턴과
   **구조적으로 다름** — 프로파일 게이트(exposeInternalDetail) 조차 없는 **무조건 sanitize**.
2. **회귀 가드(`common/UncaughtExceptionHandlerTest.kt`)**: `BoomController` 가 센티넬 메시지
   (`"내부 전용 메시지 - 응답에 노출되면 안 됨"`)를 던지고, 테스트가 `$.detail` 이 **정확히 안전 상수와 같은지**
   `value(...)` 로 단언(`:33`). 누가 detail 을 `ex.message` 로 바꾸는 회귀를 넣으면 값 불일치로 **즉시 실패**.
   → triplan `6a85b153` 이 제안한 `isNull` 가드보다 **오히려 강한** 정확값 단언.

**결론: 내 경계(bueongi)에는 QG-2 액티브 vuln 없음 + 회귀 가드 보유 → drift 0, 미고정 갭 0. 코드 변경 불요.**
(새 테스트 추가는 이미 커버된 로직에 대한 억지 작업 — consensus 가 반복 금지한 busywork 이므로 의식적으로 미추가.)

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, tests=150/0/0/0(클린). 소스/테스트 무변경이므로 베이스라인 유지.
- 코드 정독: `ApiExceptionHandler.kt:220-230`, `UncaughtExceptionHandlerTest.kt` 전문.
- 시크릿 비노출(예외 메시지·키 값 미기재).

## 잔여·인계
1. **share 계약(22~25 봉인) + 500 catch-all 정보노출(27 교차검증) 모두 미고정 갭 0**: 다음 사이클도
   베이스라인 그린(150) 확인 후 **nothing-to-do 가 정상**(억지 마이크로 가드 금지).
2. **Frontend `78eb6275`(보호자 웹 지도) 지금 연동 가능**: `docs/API.md` §`/share` 4종.
   경로 `/share`(not `/api`), POST(not PUT), DELETE 없음, 조회 항상 200+`expired`, TTL 1~3h.
   GET location 응답 = `lat`/`lng`/`updated_at`(snake_case)/`expired`.
3. **신규 `556ffb65`·`6a85b153` 는 triplan(`/v1/`·core-api) — bueongi 아님**. bueongi backend 신규 태스크
   등장 시에만 착수. 칸반·베이스라인 테스트 수·신규 소스↔테스트 매핑은 매 사이클 코드로 교차 확인.

## 메타
- 엔진: Claude(opus, backend). escalate/report-issue 미호출(새 막힘·버그 없음 — 이미 안전+가드됨). 시크릿 비노출.
- 베이스라인 tests=150/0/0/0. 변경 파일 0(보고서·메모리 제외).
