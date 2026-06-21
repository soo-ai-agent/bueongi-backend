# 17회차 — 보안 교차검증: Kakao REST 키 서버측 격리 확인 (nothing-to-do)

메타: roleId=backend / engine=claude(opus) / 2026-06-18 KST / projectPath=apps/bueongi/backend

## 요청
Dorothy 자동 루프(backend 역할) 다음 한 사이클. 깨진 빌드/테스트 우선 수리, 없으면 백로그 발굴 후 검증 가능한 산출물 1개. 상태 복원(consensus 16회차·handoff 16회차·에이전트 정의).

## 진행
- 상태 복원: `consensus.md`(312줄, 16회차)·`handoff.md`(16회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음. (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준 진행.)
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, XML 집계 **tests=108 / skipped=0 / failures=0 / errors=0**(클린 5 executed). 깨진 빌드/테스트 없음.
- TODO/FIXME/XXX: `src/` 0건.
- **점검 각도(신규) — 칸반(PM 입력) + 활성 보안 발굴 교차검증**: 에이전트 정의의 "입력: PM 태스크(Kanban)"를 직접 점검. `list_tasks`(18건) 중 bueongi backend projectPath 에 할당된 태스크 **없음** 확인:
  - `5f2bced5` SEC-KAKAO-REST-KEY-IN-BUNDLE → Project=triplan-frontend + soo-auth-service (bueongi 아님, 내 경계 밖).
  - `bscan-19` bueongi 요구사항 명세 부재 → Project=bueongi/**frontend-src** (백엔드 아님).
  - `9694868a` 대시보드 기동 상태 표시 → 오케스트레이터/대시보드 영역(bueongi 백엔드 코드 아님).
- 그러나 `5f2bced5`(보안검토 #208, Medium)는 **"Kakao REST 키가 client 번들에 인라인 노출"**이라는 위험 *클래스*를 제기 → bueongi 백엔드에도 같은 클래스 위험이 있는지 코드로 교차검증(이전 16사이클 미수행 각도).

## 산출물
코드 변경 없음(정당한 nothing-to-do). 검증 가능한 산출물 = **베이스라인 108 그린 재확인 + 보안 자세 교차검증 결과**:

**결론: bueongi 백엔드는 triplan 의 SEC-KAKAO-REST-KEY-IN-BUNDLE 과 같은 클래스 취약점이 없다.** bueongi 는 triplan 이 retrofit 하려는 "서버측 code→token 프록시" 패턴을 **이미 올바르게 구현**한 아키텍처.
- Kakao REST 키 유입은 서버측 단일 경로뿐: `application.yml:13 kakao-rest-api-key: ${KAKAO_REST_API_KEY:}` → `KakaoPlaceProvider.kt:19 @Value` 주입. 키는 Spring 백엔드 프로세스 env 에만 존재.
- 프론트는 키를 만지지 않음 — `/api/places/search` 를 호출하고 백엔드가 Kakao 를 **서버측에서** 프록시(`KakaoPlaceProvider`). 즉 REST 키가 클라이언트 번들로 새는 경로 자체가 없음(triplan 의 `kakaoSdk.ts` 브라우저 직접 OAuth 토큰 교환과 대조).
- `/api/health` 누출 점검: 응답 `HealthStatus{status, service, placeSource}` 의 `placeSource` 는 `RemotePlaceProvider.isEnabled` **불리언에서 파생한 라벨("live"/"seed")**일 뿐, 키 문자열을 절대 싣지 않음(`HealthController.kt`).
- grep 결과 `src/` 내 키 참조 4곳(application.yml·KakaoPlaceProvider·PlaceCatalog 주석) 전부 서버측 또는 주석 — 응답/로그/번들 노출 경로 없음.

## 검증
- `./gradlew test --rerun-tasks` → BUILD SUCCESSFUL. `build/test-results/test/*.xml` 집계 tests=108/skipped=0/failures=0/errors=0(변동 없음, 코드 무변경).
- `grep -rn "KAKAO_REST_API_KEY|kakao-rest-api-key|restApiKey" src/` → 서버측 4곳만. `HealthController.kt` 정독 → 키 미노출(라벨만).
- 칸반 18건 중 bueongi backend 할당 0건 직접 확인.
- 신규 파일: 이 보고서 1개. 소스/테스트/문서 **무변경**.

## 잔여·인계
- **왜 코드를 안 고쳤나**: bueongi 백엔드는 이미 보안적으로 올바름(서버 프록시 패턴). triplan 의 발굴은 bueongi 백엔드에 적용되지 않음(다른 projectPath·다른 아키텍처). 억지 변경은 금지 원칙 위반.
- **포화 상태 유지**: 도메인 검증·운영 가시성·에러 계약·입력 가드 단일화·외부/클라이언트/시드 3대 좌표 경계·도메인 안전 불변식·CORS 보안·API.md·README 전부 완성. 16사이클 합의대로 nothing-to-do 가 정상.
- 새 가치는 (a) Frontend(#1, projectPath 밖) 진행, (b) 실 외부 API 키 주입(MVP 범위 밖·합의된 보류, 재에스컬레이션 금지)에서만 발생.
- escalate/report-issue 호출 없음(새 막힘 아님). git 커밋/push 없음(승인 영역). secret 값 미출력. 사용량 제한 미도달·우회 없음.
