# 작업 보고서 — 프레임워크 경계 에러 404/500 표준화 (에러 계약 불변식 완성)

- 메타: roleId=backend / 엔진=claude(opus) / 2026-06-17 19:47 KST
- projectPath: /Users/soo/workspace/source-code/apps/bueongi/backend
- 커밋: 없음(승인 영역, master 커밋 0개 유지)

## 요청
자동 루프 backend 사이클. 깨진 빌드/테스트 우선 수정 후 검증 가능한 산출물 최소 1개 생성.
도메인 검증·운영 가시성이 포화된 상태에서 잔여 백엔드 후보(consensus Next Action #8: 404/500 프레임워크 경계 에러에 `code` 부여) 수행.

## 진행
1. 상태 복원(consensus.md / handoff.md / 에이전트 정의 `bueongi-backend.md`).
2. 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 80개 통과 — 깨진 것 없음.
3. 코드 점검으로 "모든 에러 응답에 `code`" 불변식의 마지막 빈틈 확인: ApiExceptionHandler 가 405/415까지만 처리 → 404(미지정 경로)·500(미처리 예외)은 Spring 기본 처리(`code` 없음).
4. 직전 핸드오프가 경고한 trade-off 해소 방안 채택:
   - 404: Spring Boot 3.2+ 가 미매칭 경로에서 `NoResourceFoundException` 을 기본으로 던짐 → 추가 설정 없이 핸들러만 추가.
   - 500: 응답 본문엔 내부 details 비노출 + 스택트레이스는 `log.error` 로 서버 로그에만 → 보안/디버깅 양립.

## 산출물 (소스 1 수정 / 테스트 1 보강 + 1 신규, 80→82)
- `common/ApiExceptionHandler.kt`(수정): `handleNotFound(NoResourceFoundException)`→404 `NOT_FOUND`, `handleUncaught(Exception)`→500 `INTERNAL_ERROR`(+logger, +import 2).
- `common/ApiExceptionHandlerTest.kt`(보강, +1): 미매칭 경로 `GET /api/does-not-exist`→404+`NOT_FOUND`.
- `common/UncaughtExceptionHandlerTest.kt`(신규, +1): 테스트 전용 `BoomController` 로 미처리 예외→500+`INTERNAL_ERROR`+내부 메시지 비노출 회귀 가드.
- 기존 계약/엔드포인트 불변(additive). 결과: 405/415/404/500 전부 `code` 부여 → "모든 에러 응답에 code" 불변식 완성.

## 검증
- `./gradlew test` BUILD SUCCESSFUL.
- 테스트 82개 전부 통과(failures=0, errors=0). `build/test-results/test/*.xml` 집계 tests=82 확인.
- 404/500 핸들러 동작 및 500 내부 details 비노출 모두 슬라이스 테스트로 고정.

## 잔여·인계
- Frontend(#1, 명확한 병목): `/api/routes/compare` 클라이언트 + 4종 422/400 및 405/415/404/500 `code` 분기. 백엔드 계약 전부 준비 완료.
- Backend: 도메인 검증·운영 가시성·에러 계약 전부 포화·완성. 잔여 실질 후보는 실 외부 API(Tmap/Kakao·공공데이터) 연동뿐 — 키/네트워크 의존, MVP 범위 밖.
- consensus.md / handoff.md 갱신 완료. git 커밋·push·secret 노출·사용량 우회 없음.
