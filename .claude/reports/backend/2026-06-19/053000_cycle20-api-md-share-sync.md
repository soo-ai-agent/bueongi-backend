# 20회차 — `docs/API.md`에 위치 공유 계약 동기화

## 요청
자동 루프(backend) 다음 한 사이클. 깨진 빌드/테스트 우선 수정, 없으면 검증 가능한 산출물 1개. 게이트 미해소(PM 비준·Redis 승인) 시 인프라 불요한 가치 발굴.

## 진행
- 상태 복원: `consensus.md`(19회차)·`handoff.md`(19회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음. (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, **tests=139/0/0/0**(클린). 깨진 빌드/테스트 없음.
- 칸반 점검(`list_tasks` 24건): 내 영역 `cdb5314d` 70% 백로그(Redis 게이트 대기), PM `03b7bcf7` 0%(게이트 미해소), 내 영역 신규 태스크 0건.
- **갭 발굴(13/16회차 패턴 — "역할 정의 출력물"이 모두 산출됐는가)**: 에이전트 정의의 출력물은 "API 계약 명세 — frontend와 공유". 19회차가 위치공유 4종 엔드포인트를 **구현**했으나 canonical 레퍼런스 `docs/API.md`(6/18 17:31)에는 미반영 — `grep -i share docs/API.md` = 0건. 계약이 `docs/LOCATION_SHARING_DESIGN.md`(설계서)에만 있어, 프론트 `78eb6275`(보호자 웹, 핸드오프가 "지금 병행 가능"이라 한 작업)가 단일 API 레퍼런스에서 share 계약을 못 찾는 실제 미충족 deliverable.

## 산출물
`docs/API.md` 4개 편집(코드 직접 파생, drift 0 교차검증):
1. `GET /api/health` 응답에 `locationStore`("redis"|"memory") 필드 추가.
2. 에러 code 전체 표에 `SHARE_SESSION_NOT_FOUND`(404, "Share Session Not Found") 행 추가 — 도메인 404 vs `NOT_FOUND` 구분 명기.
3. **신규 섹션 `## /api/share/sessions — 실시간 위치 공유`**: 4종 엔드포인트(POST 시작 / PUT 갱신 / GET 조회 200·204·404 3분기 표 / DELETE 멱등) 요청·응답 shape·필드 의미·프라이버시(랜덤 토큰·PII 미저장·TTL)·저장소 추상화(Redis↔인메모리 폴백, health 라벨) 명기.
4. 도메인 상수 표에 `DEFAULT_SESSION_TTL_SECONDS`(6h)·`MIN/MAX_SESSION_TTL_SECONDS`(60s/12h)·`LOCATION_TTL_SECONDS`(90s) 추가.

## 검증
- 소스/테스트 **무변경**(문서만) → 139 그린 유지(편집 전 클린 재실행 확인).
- drift 교차검증(grep): TTL 상수 4종(60/43_200/21_600/90) ↔ `ShareDtos.kt` 일치, health 라벨 "memory"/"redis" ↔ `LocationStore.kt`/`InMemoryLocationStore.kt` 일치, code/title ↔ `ApiExceptionHandler.kt` 일치, 4종 엔드포인트·204/404 분기 ↔ `ShareController.kt`/`ShareSessionService.kt` 일치.
- 시크릿 비노출(Redis URL·자격증명·토큰 값 미기재 — 라벨/구조만). 칸반 `cdb5314d` 70%→75%.

## 잔여·인계
1. **Redis 구현 게이트(미해소)**: PM(`03b7bcf7`, 0%) 비준 + Redis 인프라 승인 → `RedisLocationStore` + `spring-boot-starter-data-redis` + `BUEONGI_REDIS_URL`. 인메모리 계약/테스트 31건은 회귀 가드 유지. health `locationStore`가 "redis"로 바뀌는지 검증.
2. **Frontend `78eb6275` 지금 병행 가능**: 이제 `docs/API.md` 하나로 share 4종 계약 전체 참조 가능(설계서 안 봐도 됨). 204(첫 위치 전)·404(만료)·`stale` 3분기 주의.
3. 게이트 미해소 + 새 칸반 없으면 nothing-to-do 정상(억지 테스트 금지). escalate/report-issue 미호출(Redis 승인은 PM 비준과 함께 — 현 단계 막힘 아님, 새 실패 없음).
