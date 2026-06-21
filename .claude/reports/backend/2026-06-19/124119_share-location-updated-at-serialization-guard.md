# 작업 보고서 — GET location 응답 `updated_at` snake_case 직렬화 회귀 가드 (25회차)

- 역할: backend (bueongi-backend) / 엔진: Claude(opus)
- 일시: 2026-06-19 12:41 KST
- 사이클: 자동 루프 25회차

## 요청
- Dorothy 자동 루프 backend 사이클: 직전 상태(consensus/handoff 24회차) 복원 → 깨진 빌드/테스트 우선 수정 → 최소 1개 검증 가능한 산출물 → 메모리 갱신.

## 진행
1. 상태 복원: consensus.md(24회차)·handoff.md(24회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음.
2. 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, tests=149/0/0/0(클린 5 executed). 깨진 빌드/테스트 없음.
3. 칸반 점검(`list_tasks` 26건): 내 영역 `cdb5314d` planned 100% done, backend projectPath 신규 할당 0건, 나머지 backend 태스크 전부 triplan/soo-auth-service 경계 밖. 24회차 핸드오프와 정확히 일치(메모리 stale 아님).
4. 점검 각도(24회차에서 한 단계 더): 서버가 서빙하는 `guardian.html`(backend 소유 client)이 폴링으로 읽는 JSON 필드명 ↔ 서버 `LocationView` 직렬화 정합성을 데이터 흐름으로 추적. 네 필드(`expired`/`lat`/`lng`/`updated_at`) 전부 일치(실 버그 없음) 확인.
5. 미고정 갭 발견: `LocationView.updatedAt` 은 `@JsonProperty("updated_at")` 로 snake_case 매핑, guardian.html:84 `renderLive(data)` 가 `data.updated_at` 을 읽음. 그러나 기존 GET location 테스트(`ShareControllerTest:71-88`)는 lat/lng/expired 만 단언하고 `updated_at` 필드명 미검증(POST location 의 updated_at 단언은 보호자가 안 읽는 다른 DTO=`UpdateLocationResponse`). `@JsonProperty` 제거 시 camelCase `updatedAt` 직렬화 → 149개 통과하지만 보호자 페이지 '마지막 갱신' 표시가 조용히 사라지는 silent 회귀.

## 산출물
- `src/test/kotlin/io/soo/bueongi/share/ShareControllerTest.kt`에 테스트 1건 추가(14→15):
  위치 POST 후 GET location 응답이 ① `$.updated_at` 존재(guardian.html:84 가 읽는 필드명) ② `$.updatedAt`(camelCase 회귀 형태) 부재(`doesNotExist`)를 단언.
- 소스 무변경(순수 가산적 회귀 가드, 외부 동작/계약 완전 불변).
- 메모리 갱신: consensus.md·handoff.md 25회차 섹션 + Next Action.

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, tests=150/0/0/0(149→+1, 클린 5 executed).
- 필드명 교차검증: guardian.html(`expired`/`lat`/`lng`/`updated_at`) ↔ `LocationView`/`@JsonProperty` 전부 일치.

## 잔여·인계
- share 보호자 페이지 3중 계약 봉인 완성(서버측 escapeJs 23회차 + 클라이언트측 encodeURIComponent 24회차 + 필드명 직렬화 updated_at 25회차). 미고정 갭 0.
- 다음 사이클: 베이스라인 그린(150) 확인 후 nothing-to-do 가 정상(억지 테스트/리팩터 금지 — 보안 분기·소스↔테스트 매핑·client↔server 계약 모두 포화).
- Frontend `78eb6275`(보호자 웹 지도) 지금 연동 가능. GET location 응답 필드 = `lat`/`lng`/`updated_at`(snake_case)/`expired`.
- escalate/report-issue 미호출(새 막힘·버그 없음 — 구현 정상, 회귀 방어만 추가). 시크릿 비노출.
