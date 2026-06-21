# backend 사이클 15 — API.md 에러 code 표 + 엔드포인트 shape drift 독립 검증 (nothing-to-do)

- 역할: bueongi-backend (Claude opus)
- 일시: 2026-06-18 19:49 KST (15회차)
- 결론: **nothing-to-do (산출물: API.md 미검증 구역 독립 drift 검증 — drift 0, 전 구역 정확성 보증 완료)**

## 요청
Dorothy 자동 루프 backend 사이클. 직전 상태 복원 → 깨진 빌드/테스트 우선 수정 → 최소 1개 검증 가능 산출물 → 메모리 갱신.

## 진행
1. 상태 복원: `consensus.md`(298줄, 14회차)·`handoff.md`(14회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음.
   (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준 진행.)
2. 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, **tests=108 / skipped=0 / failures=0 / errors=0**(클린 5 executed). 깨진 빌드/테스트 없음.
3. 칸반 점검: bueongi backend의 Frontend `/api/routes/compare` 연동 작업은 별도 등록 없음. 단 이는 14사이클째 consensus Next Action #1로 추적 중이고 frontend 역할(bueongi-dev) 영역 → backend가 칸반에 새로 정의하는 것은 작업 경계 침범·중복 보고라 미등록.
4. **점검 각도(신규)**: 14회차의 `docs/API.md` drift 검증은 `RouteType` 중심(distanceFactor·밀도 8값·상수·포트/CORS)이었고 **에러 code 10종 표 + 4개 엔드포인트 요청/응답 shape는 독립 대조되지 않았다**. frontend 분기의 핵심(code 10종·응답 필드)이므로 이 미검증 구역을 코드와 1:1 대조.

## 산출물
**`docs/API.md` 미검증 구역의 코드 ↔ 문서 독립 drift 검증 → drift 0.** 소스/테스트/문서 **무변경**(신규는 이 보고서 1파일뿐).

| API.md 항목 | 출처 코드 | 결과 |
|---|---|---|
| 에러 code 10종 표(HTTP status·title·code·errors 유무) | `common/ApiExceptionHandler.kt` | 10/10 일치 |
| RouteRequest shape(origin nullable·destination @NotNull·routeType optional) | `routes/RouteDtos.kt` | 일치 |
| 가드 순서 ORIGIN_REQUIRED→SAME_ORIGIN_DESTINATION→TRIP_TOO_FAR | `validatedOrigin()` | 일치 |
| RouteOption/RouteTag(variant 5종) shape | `routes/RouteDtos.kt` | 일치 |
| FacilityPoi/FacilitySummary/FacilitiesResponse shape | `routes/FacilityDtos.kt` | 일치 |
| PlaceItem(name/address/lat/lng) shape | `places/PlaceDtos.kt` | 일치 |
| HealthStatus(status="UP"/service="bueongi-backend"/placeSource live\|seed) | `health/HealthController.kt` | 일치 |
| MAX_KEYWORD_LENGTH=100 / MAX_PER_TYPE=8 | `PlaceSearchService`·`FacilityService` | 일치 |
| MIN_TRIP=1.0 / MAX_TRIP=100_000.0 | `routes/RouteDtos.kt` | 일치 |

**결론: 에러 code 표 10종 + 4개 엔드포인트 요청/응답 shape + 상수 출처 전부 코드와 정확히 일치. 14회차(RouteType 구역)와 합쳐 `docs/API.md` 전 구역 독립검증 완료 — 프론트 연동 참조 문서로 전면 신뢰 가능.**

(사소 관찰: API.md 출처 표기가 패키지 prefix `io/soo/bueongi/` 를 생략하나 전 항목 일관 컨벤션이라 drift 아님. 14줄 일괄 변경은 busywork라 미수정.)

## 검증
- `./gradlew test --rerun-tasks` → BUILD SUCCESSFUL, `build/test-results/test/*.xml` 집계 tests=108/skipped=0/failures=0/errors=0(클린 5 executed).
- API.md drift: `ApiExceptionHandler.kt`·`RouteDtos.kt`·`FacilityDtos.kt`·`PlaceDtos.kt`·`HealthController.kt` 정독 + `MAX_KEYWORD_LENGTH`/`MAX_PER_TYPE`/리터럴 grep 으로 코드와 1:1 대조 → drift 0.
- 소스/테스트/문서 무변경.

## 잔여·인계
- **Frontend(#1, 유일한 명확 병목·projectPath 밖)**: `/api/routes/compare` 클라이언트 + 응답 분기(400 VALIDATION_FAILED·422 ORIGIN_REQUIRED/SAME_ORIGIN_DESTINATION/TRIP_TOO_FAR). 백엔드 계약 전부 준비·완성, `docs/API.md` 전 구역 독립검증 완료 — 프론트가 이 문서 하나로 연동 가능.
- **Backend 실질 잔여 없음**: main 소스 19개 전부 전용 테스트 보유, 도메인·에러·CORS·좌표 경계·도메인 안전 불변식 포화. `docs/API.md` 전 구역 정확. 계약 변경 없는 한 다음 사이클도 API.md 재검증/재작성 불요.
- escalate/report-issue 호출 없음(외부 API 키는 합의된 보류 — 새 막힘 아님, 재요청 금지).
- 다음 사이클 권장: 베이스라인 그린 확인 후 실제 미고정 갭 없으면 nothing-to-do 보고가 정상(억지 테스트/리팩터/문서 금지). 새 가치는 (a) Frontend 진행 또는 (b) 실 외부 API 키 주입에서만 발생.
