# 작업 보고서 — origin 미확보 정책 확정(422 ORIGIN_REQUIRED)

- 메타: roleId=backend / 엔진=Claude(opus) / 일시=2026-06-17 08:40 (KST)
- 프로젝트: bueongi-backend (`/Users/soo/workspace/source-code/apps/bueongi/backend`)
- 대상 작업: consensus.md Next Action #1

## 요청
`/api/routes/compare` 의 origin 미확보(현재 위치 권한 거부/미지원) 정책 확정 —
기본 origin 사용 또는 422 메시지 계약 정의, 표준 에러 스키마(ProblemDetail) 재사용, Frontend 위치권한 흐름과 연계.

## 진행
1. 상태 복원(consensus/handoff/에이전트 정의) + 베이스라인 `./gradlew test` SUCCESSFUL(43개) 확인.
2. 정책 결정: **기본 origin 대체 거부 → HTTP 422 + `code:"ORIGIN_REQUIRED"`**.
   근거: 안심 귀가 앱에서 임의 출발지는 잘못된 "안전 경로"를 만들어 사용자 안전을 해침. 422는 사용자 행동으로 복구 가능한 상태.
3. "잘못된 입력(400)"과 "미확보(422)" 구분: origin 값이 있으면 `@Valid` 좌표 검증으로 여전히 400, 키 누락만 422.

## 산출물
- `common/ApiExceptionHandler.kt`: `OriginRequiredException` + 핸들러(422, title="Origin Required", code="ORIGIN_REQUIRED").
  기존 400 핸들러에 `code` 추가(VALIDATION_FAILED / MALFORMED_REQUEST) — 에러 응답 전반 머신리더블화.
- `routes/RouteDtos.kt`: `RouteRequest.origin` @NotNull 제거(@Valid 유지) + `requireOrigin()` 가드. destination @NotNull 유지.
- `routes/RouteService.kt`, `routes/FacilityService.kt`: `origin!!` → `requireOrigin()`.
- 테스트: `ApiExceptionHandlerTest`(3→5), `RouteControllerTest`(origin 케이스 422로 갱신).

## 검증
- `./gradlew test` BUILD SUCCESSFUL, **45개 전부 통과**(failures=0, errors=0). 직전 43 → +2.
- 신규/변경 회귀: compare·facilities origin 누락 → 422 ORIGIN_REQUIRED, origin 있고 범위초과 → 400 VALIDATION_FAILED.

## 잔여·인계
- Frontend(Next Action #1): 422 `ORIGIN_REQUIRED` 수신 시 위치 권한 재요청 / 출발지 직접 선택 UI 유도(에러 토스트 아님). `/api/routes/compare` 클라이언트 스켈레톤.
- Backend(Next Action #2): Kakao Local 실연동 스켈레톤(키 폴백).
- 계약 변경은 consensus.md 베이턴으로 통지(frontend 에이전트 직접 메시지 미사용).
- git 커밋/push 없음(승인 영역). 사용량 제한 미도달, 우회 없음.
