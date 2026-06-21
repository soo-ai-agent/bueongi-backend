# 작업 보고서 — 프레임워크 레벨 에러(405/415) 표준화

- 메타: roleId=backend / 엔진=Claude(opus) / 일시=2026-06-17 18:39 (KST) / projectPath=`/Users/soo/workspace/source-code/apps/bueongi/backend`
- 슬러그: framework-error-code-405-415

## 요청
자동 루프 backend 사이클. 깨진 빌드/테스트 우선 수정 후 최소 1개의 검증 가능한 산출물 생성. 명확한 할 일이 없으면 백로그 발굴.

## 진행
- 상태 복원: consensus.md / handoff.md / 에이전트 정의(`bueongi-backend.md`) 읽음.
- 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 78개 통과(깨진 것 없음).
- 백로그 발굴(스킬 부재 → 코드 직접 점검): 도메인 검증 계약은 포화. 그러나 **에러 계약 불변식("모든 에러 응답에 `code`")의 빈틈** 발견 —
  `ApiExceptionHandler` 가 애플리케이션 예외만 처리해, 프레임워크 레벨 에러 405(POST 전용 엔드포인트에 GET)·415(비-JSON Content-Type)는
  Spring 기본 처리(빈 본문, `code` 없음)로 새어나가 프론트의 `code` 분기 계약을 깼다. `spring.mvc.problemdetails.enabled` 미설정(false) 확인으로 실증.
- 수행: 두 `@ExceptionHandler` 추가. 405 는 HTTP 규약상 `Allow` 헤더를 보존하려 `ResponseEntity<ProblemDetail>` 로 반환.

## 산출물
- 소스 1 수정: `src/main/kotlin/io/soo/bueongi/common/ApiExceptionHandler.kt`
  - `handleMethodNotAllowed(HttpRequestMethodNotSupportedException)` → 405 `code:"METHOD_NOT_ALLOWED"` + `Allow` 헤더.
  - `handleUnsupportedMediaType(HttpMediaTypeNotSupportedException)` → 415 `code:"UNSUPPORTED_MEDIA_TYPE"`.
- 테스트 1 보강: `src/test/kotlin/io/soo/bueongi/common/ApiExceptionHandlerTest.kt` (+2: 405+Allow+code / 415+code).
- 기존 계약/엔드포인트 불변(additive). 신규 파일 없음.

## 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 **80개 전부 통과**(failures=0, errors=0). 78 → +2. (`build/test-results/test/*.xml` tests=80 failures=0 errors=0.)

## 잔여·인계
- Frontend(최우선 병목): compare 클라이언트 + `code` 분기. 백엔드 계약(4종 422/400 + 405/415) 준비 완료.
- Backend(후순위): 404/500 에러 경계 `code` 부여 — 404 가로채기 설정/500 catch-all dev 디버깅 trade-off 판단 필요(consensus Next Action #8).
- git 커밋·push 없음(승인 영역). secret 비노출. 사용량 제한 미도달.
