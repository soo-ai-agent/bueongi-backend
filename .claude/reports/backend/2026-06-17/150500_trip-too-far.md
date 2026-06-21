# 작업 보고서 — TRIP_TOO_FAR 원거리 상한 도메인 검증

- 메타: roleId=backend / engine=claude(opus) / 2026-06-17 15:05 KST / projectPath=apps/bueongi/backend

## 요청
자동 루프 backend 사이클. consensus/handoff 복원 후 Next Action 수행. 직전 사이클 Next Action #4(선택): "출발-도착이 비현실적으로 먼 경우 상한" 도메인 예외 추가, #5: SAME_ORIGIN_DESTINATION 컨트롤러 커버 재확인.

## 진행
1. 베이스라인 확인: `./gradlew test` BUILD SUCCESSFUL, 72개 통과(깨진 빌드 없음).
2. Next Action #1(Frontend)은 projectPath 경계 밖 → backend 산출물 #4 선택.
3. SAME_ORIGIN_DESTINATION(거리 하한) 패턴을 그대로 본떠 거리 상한 가드 구현.
4. 기존 테스트 좌표가 새 임계(100km)에 걸리는지 점검 → FacilityServiceTest 580km 케이스 충돌 발견·조정.
5. #5 검토: ApiExceptionHandlerTest 가 compare/facilities 양쪽 이미 커버 → 작업 불요 확인.

## 산출물 (소스 4 파일 / 테스트 +4, 72→76)
- `common/ApiExceptionHandler.kt`: `TripTooFarException(distanceMeters, maxMeters)` + 422 핸들러(`code:"TRIP_TOO_FAR"`, errors[destination]).
- `routes/RouteDtos.kt`: `requireWithinReach(origin)` 가드 + `MAX_TRIP_DISTANCE_METERS=100_000.0`(100km).
- `routes/RouteService.kt`, `routes/FacilityService.kt`: `requireDistinctEndpoints` 직후 `requireWithinReach` 호출.
- 테스트: RouteServiceTest +2(초과 예외/89km 경계 정상), FacilityServiceTest +1(초과 예외) + 기존 cap 테스트 좌표 89km 조정, ApiExceptionHandlerTest +1(HTTP 422 본문 계약).

## 검증
- `./gradlew clean test` → BUILD SUCCESSFUL. 테스트 76개 전부 통과(failures=0, errors=0). build/test-results XML 집계로 확인.

## 잔여·인계
- Frontend(최우선): compare 클라이언트에서 422 TRIP_TOO_FAR 수신 시 도착지 재선택 유도("100km 이내" 안내). 백엔드 계약 준비 완료.
- Backend: 도메인 검증 계약 포화. 잔여는 실 외부 라우팅/공공데이터 API 연동(키·네트워크 의존, MVP 범위 밖).
- 계약 변경 통지는 consensus.md/handoff.md 베이턴 기록(SendMessage 미사용). git 커밋 안 함(승인 영역).
