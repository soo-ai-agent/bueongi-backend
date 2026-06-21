# 작업 보고서 — compare/facilities 입력 가드 단일화

## 메타
- roleId: backend / 엔진: Claude(opus)
- 일시: 2026-06-17 20:30 (KST)
- projectPath: /Users/soo/workspace/source-code/apps/bueongi/backend
- 사이클 유형: 자동 루프(backend) — 백로그 발굴 기반

## 요청
- Dorothy 자동 루프 backend 사이클: 직전 상태(consensus/handoff) 복원 후 "다음 한 사이클" 수행.
- 깨진 빌드/테스트 우선 수정, 최소 1개의 검증 가능한 산출물 생성. nothing-to-do 시 백로그 발굴.

## 진행
- 상태 복원: consensus.md / handoff.md / 에이전트 정의(bueongi-backend.md) 읽음.
- 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 82개 통과 — 깨진 빌드 없음.
- 도메인 검증·운영 가시성·에러 계약 포화 상태 → 코드 직접 점검(백로그 발굴) 수행.
- 발견: `RouteService.compare` / `FacilityService.alongRoute` 가 입력 가드 3종
  (`requireOrigin`→`requireDistinctEndpoints`→`requireWithinReach`)을 각자 같은 순서로 복제 나열.
  → 한쪽만 수정 시 두 엔드포인트 검증이 어긋날 드리프트 리스크(안심앱에서 안전 계약 훼손). 단일 진실 원천으로 통합 결정.

## 산출물
- `routes/RouteDtos.kt`: `RouteRequest.validatedOrigin()` 추가 — 가드 3종을 고정 순서로 적용·검증된 출발지 반환(단일 진실 원천).
- `routes/RouteService.kt` / `routes/FacilityService.kt`: 가드 3줄 나열 → `validatedOrigin()` 1줄 호출로 교체(그 외 무변경).
- `routes/RouteRequestGuardTest.kt`(신규, 5건): 정상/ORIGIN_REQUIRED/SAME_ORIGIN_DESTINATION/TRIP_TOO_FAR + 가드 순서 고정.
- 순수 내부 리팩터 — 외부 동작·응답 계약(422 4종 거동) 완전 불변.

## 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 87개 전부 통과(failures=0, errors=0). 82→87(+5). `build/test-results/test/*.xml` 집계 확인.
- 기존 RouteService/FacilityService/ApiExceptionHandler 테스트 무변경 통과 → 거동 보존 증명.

## 잔여·인계
- Frontend(명확한 다음 병목): `/api/routes/compare` 클라이언트 + 422/400 code 분기. 백엔드 계약 전부 준비 완료.
- Backend 실질 잔여 없음(포화). 다음 사이클 nothing-to-do 시 백로그 발굴 재수행 권장.
- git 커밋·push 안 함(승인 영역). secret 비노출, 사용량 제한 미도달.
