# backend 사이클 36 — refresh token family 봉쇄 클래스 share 토큰 교차검증 (nothing-to-do)

메타: 역할=backend / 엔진=Claude(opus) / 날짜=2026-06-21 01:10 KST / 사이클=36

## 요청
Dorothy 자동 루프 backend 사이클. consensus.md(35회차)·handoff.md(35회차)·에이전트 정의 복원 후 "다음 한 사이클" 결정적 수행. 깨진 빌드/테스트 최우선 수정, 최소 1개 검증 가능 산출물.

## 진행
- 상태 복원: consensus.md(35회차)·handoff.md(35회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음. (`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 5 executed. XML 집계 **tests=150/0/0**(23 suite). 깨진 빌드/테스트 없음 — 35회차와 일치.
- 칸반 점검(`list_tasks` **33건**, 35회차와 동일 건수): 내 영역 `cdb5314d` planned 100% done. bueongi backend 신규 할당 0건. 신규/변경 backend 태스크 없음. backlog의 `f8aaf6dd`(SEC-REFRESH-FAMILY-1, soo-auth)·`5f2bced5`·`79e12f6b`·`c7e93835`(triplan/soo-auth)·`bscan-19`·`0e823341` 모두 경계 밖.
- **점검 각도(27~35회차 패턴 — 활성 형제 보안 발굴을 내 경계에 코드로 교차 적용, 36회차까지 미점검 신규 각도)**: `f8aaf6dd`(SEC-REFRESH-FAMILY-1 — "refresh 토큰 재사용 탐지 후 token family 봉쇄 부재")는 직전 사이클들이 "경계 밖"으로만 분류하고 코드 교차검증 미수행. 위험 클래스("토큰이 탈취·재사용될 때 회전/무효화 방어")가 bueongi 의 유일한 토큰 메커니즘인 **share 토큰**에 있는가를 코드로 독립 검증.

## 산출물
순수 검증(소스/테스트/문서 변경 0건). 교차검증 결론 = **구조적 부재(drift 0, 갭 0)**:
1. **토큰 회전 자체가 부재(전제 부재)**: share 토큰은 `ShareService.create()`에서 1회 발급 후 절대 회전·재발급 안 됨(`ShareService.kt:71 newToken()`은 생성 경로에서만 호출). `updateLocation`(:45)·`getLocation`(:57)은 동일 토큰으로 `store.find` lookup만 하고 새 토큰을 발급하지 않음. refresh-family 방어의 전제("토큰 회전 → 구 토큰 무효화 → 폐기된 구 토큰 재제시 = 탈취 신호") 토대 자체가 없음.
2. **인증 토큰이 아닌 capability(공유 링크) 토큰**: share 토큰은 access/refresh 토큰처럼 신원/세션을 인증하지 않음. "이 링크를 가진 사람이 위치를 본다"는 익명 공유 모델(PII 미저장). 재사용이 정상 동작(보호자 웹이 5초 폴링으로 같은 토큰 계속 재사용, `getLocation`) → "재사용 탐지 후 봉쇄"는 share 모델과 의미적으로 충돌.
3. **탈취 위험은 이미 시간제한으로 봉쇄**: 상한 TTL 1~3h 자동 만료(`ShareService.kt:32` `now.plusSeconds(hours*3600)`, Redis native TTL/인메모리 lazy 비교) + SecureRandom 128-bit URL-safe 추측 불가(`:71-75`, `TOKEN_BYTES=16`). 링크가 유출돼도 최대 3시간 후 자동 무효. revoke/DELETE 엔드포인트 부재는 의도된 단순성(docs/API.md §share 4종).
4. **봉쇄 대상 "family" 개념 부재**: refresh-family 는 user→다수 세션→토큰 lineage 트리가 있어야 하는데, share 는 token 1개=세션 1개 평면 구조이고 user identity 를 저장하지 않음(PII 미저장) → 묶어서 무효화할 lineage 자체가 없음.

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, tests=150/0/0(23 suite). 베이스라인 그린 불변.
- 코드 근거: `ShareService.kt:28-39`(create=1회 발급), `:45-51`(updateLocation=lookup·회전 없음), `:57-68`(getLocation=재사용 정상), `:70-81`(SecureRandom 128-bit), `:30-32`(상한 TTL). 회전/family/revoke 식별자 grep 0건.

## 잔여·인계
- **refresh token family 봉쇄(36, 구조적 부재) + DB checksum(35) + Kakao 키 노출(34) + runbook bootRun(33) + stored XSS(32) + 환경변수 드리프트(31) + 동시성(30) + rate-limit(29) + 부팅 무결성(28) + 500 정보노출(27) + share 계약(22~26) 모두 미고정 갭 0**: 다음 사이클도 베이스라인 그린(150) 확인 후 nothing-to-do 가 정상(억지 마이크로 가드/리팩터/문서 재작성 금지).
- **bueongi share 토큰은 인증 토큰이 아닌 시간제한 capability 토큰** — 회전 없음(재사용 탐지 토대 부재) + TTL 자동 만료로 탈취 위험 제한. refresh-family revocation 클래스 구조적 부재.
- Frontend `78eb6275`(보호자 웹 지도) 지금 연동 가능: `docs/API.md` §`/share` 4종. 경로 `/share`(not `/api`), POST(not PUT), DELETE 없음, 조회 항상 200+`expired`, TTL 1~3h. GET location 응답 = `lat`/`lng`/`updated_at`(snake_case)/`expired`.
- escalate/report-issue 미호출(새 막힘·버그 없음). 시크릿 비노출(코드 식별자·라벨만).
