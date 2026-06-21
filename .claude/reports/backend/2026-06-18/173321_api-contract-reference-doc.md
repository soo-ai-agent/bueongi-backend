# 작업 보고서 — API 계약 레퍼런스 문서화 (`docs/API.md`)

- 역할: backend (bueongi-backend)
- 일시: 2026-06-18 17:33 KST
- 사이클: 13회차 (자동 루프)
- 엔진: Claude (opus)

## 요청
자동 루프 backend 사이클: 직전 상태(consensus.md/handoff.md/에이전트 정의) 복원 후, 깨진 빌드/테스트 우선 수정 + 최소 1개 검증 가능 산출물 생성. nothing-to-do 시 백로그 발굴.

## 진행
- 상태 복원: consensus.md(13회차분까지 누적), handoff.md(12회차), 에이전트 정의(`bueongi-backend.md`) 읽음. 절차가 가리킨 `.claude/agents/backend.md`·`backlog-discovery` 스킬은 부재 → 실제 존재 파일 기준 진행.
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 108개 전부 통과(클린 5 executed). 깨진 것 없음.
- 백로그 발굴(점검 각도 변경): 12회차 동안 "main 소스↔전용 테스트 커버리지 갭"만 반복 점검했음을 확인. 이번엔 **에이전트 정의의 출력물**("API 계약 명세 — frontend와 공유")이 실제 산출됐는지 점검 → backend 내 API 문서/README/docs/OpenAPI 전무, 계약이 consensus.md 누적 이력에만 산재함을 발견. Frontend(#1) 병목의 진입장벽 → 실제 미충족 deliverable로 판단.
- 핵심 소스 9개 정독(컨트롤러 3·DTO 3·서비스 3·Geo·RouteType·ApiExceptionHandler·HealthController·application.yml)으로 정확한 계약 추출 후 문서화.

## 산출물
- **`docs/API.md`(신규)** — 코드에서 직접 파생한 현재 API 계약 스냅샷:
  - 개요(Base URL·포트 8119·CORS·stateless·결정론·외부 키 없이 동작)
  - 공통 에러 스키마(RFC 7807 ProblemDetail+`code`) + **에러 code 10종 전체 표**(HTTP/title/errors/조건·프론트 처리)
  - 엔드포인트 4종(health·places/search·routes/compare·routes/facilities): 요청·200 응답 JSON 예시·필드 의미·동작 정책·가드 순서
  - 도메인 상수 한눈에 표
  - drift 방지/역할 분리 주석(코드와 함께 갱신·누적 이력은 consensus.md)
- 소스/테스트 **무변경**(문서만 추가) → 모든 엔드포인트 거동·응답 shape 완전 불변.

## 검증
- `./gradlew test --rerun-tasks` → BUILD SUCCESSFUL, tests=108 skipped=0 failures=0 errors=0(문서 추가는 빌드 영향 0).
- **drift 교차검증**: 문서의 모든 상수/리터럴을 `grep` 으로 코드와 대조 — code 10종, `MAX_KEYWORD_LENGTH=100`/`MIN_TRIP_DISTANCE_METERS=1.0`/`MAX_TRIP_DISTANCE_METERS=100_000.0`/`MAX_PER_TYPE=8`, distanceFactor(1.18/1.05/0.92), 포트 8119·CORS 3619 전부 일치 확인.

## 잔여·인계
- **Frontend(#1, 최우선·유일 병목)**: `/api/routes/compare`·`/facilities` 클라이언트 연동. 이제 `backend/docs/API.md` 하나로 계약 참조 가능(consensus 이력 안 뒤져도 됨). projectPath 밖이라 backend 가 직접 못 함.
- **Backend**: 테스트 커버리지·도메인/에러 계약·문서 전부 포화. 다음 사이클은 베이스라인 그린 확인 + `docs/API.md` 코드 동기 확인 후, 실제 갭 없으면 nothing-to-do 정상. 계약 변경 시 `docs/API.md` 동기화 필수.
- 외부 API 키 주입은 합의된 보류(MVP 범위 밖) — 재에스컬레이션 대상 아님.
- escalate/report-issue 호출 없음(새 막힘 없음). secret 노출·사용량 우회·git 커밋 없음.
