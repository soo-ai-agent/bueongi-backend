# cycle39 — RedisLocationStore 단위 커버리지 교차검증 (nothing-to-do)

- 역할: bueongi-backend / 엔진: Claude(opus) / 일시: 2026-06-21 07:27 KST (39회차)

## 요청
Dorothy 자동 루프 backend 역할 — "다음 한 사이클" 결정적 수행. 깨진 빌드/테스트 우선 수정,
없으면 최소 1개 검증 가능한 산출물 또는 정당한 nothing-to-do.

## 진행
1. **상태 복원**: consensus.md(38회차)·handoff.md(38회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음.
   (`.claude/skills/backlog-discovery/`·`.claude/skills/` 디렉토리 여전히 부재 → 실존 파일 기준.)
2. **베이스라인**: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 5 executed(150 그린). 깨진 빌드/테스트 없음 — 38회차와 일치.
3. **칸반 점검**(`list_tasks` **33건**, 38회차 동일): 내 영역 `cdb5314d` planned 100% done.
   bueongi backend 신규 할당 0건. backlog 미완료(`f8aaf6dd`/`5f2bced5`/`bscan-19`/`79e12f6b`/`c7e93835`/`0e823341`
   + 대시보드/모니터링/슬랙 E2E 3건) 전부 triplan/soo-auth/bueongi frontend-src/플랫폼-운영 — 경계 밖.
   소스 변경 0건(main 27 / test 23 .kt, 38회차 06:21 이후 변경 없음).
4. **점검 각도(39회차 — 신규)**: 38회차는 라이브 bootRun **인메모리 폴백** 런타임 smoke였음.
   미점검 각도 = **RedisLocationStore 의 직렬화/TTL/키-모델 경로가 단위 테스트로 봉인됐는가**
   (38 사이클 동안 RedisLocationStore 커버리지를 명시적 교차검증한 적 없음). 실 Redis 왕복은 외부 인프라
   영역이라, 코드로 "키/필드/직렬화/TTL 매핑 + 미커버 방어 분기 도달성"을 독립 검증.

## 산출물
**결과: 이미 봉인 + 유일 미커버 분기는 도달 불가 방어 분기(drift 0, 갭 0) — 소스/테스트/문서 변경 0건(순수 검증).**

- **RedisLocationStoreTest 5개가 키 모델 계약을 봉인**:
  ① `backend == "redis"` 라벨 ② save → `share:{token}` HASH `{created_at,expires_at}` + `location:{token}` HASH
  `{lat,lng,updated_at}` + 양 키 상한 TTL(`expire(Duration)`) ③ 위치 없는 save → location 키 미생성
  ④ find: 빈 share HASH → null(키 만료/미존재) ⑤ find: share+location HASH → ShareSession 재구성
  (`Instant.toString()`↔`Instant.parse` round-trip 검증) ⑥ find: share만·location 비면 location=null.
  → 직렬화 왕복·필드명·TTL·부분손상(location 부재) 분기 전수 커버.
- **유일 미커버 분기 = `ttlFrom` 음수/0 경계 폴백(`RedisLocationStore.kt:78`, `Duration.ofSeconds(1)`)**:
  이 분기가 ShareService 흐름에서 **도달 불가**임을 코드로 확인 →
  · `create()`(`ShareService.kt:32`): `expiresAt = now + (1~3h)` → remaining 항상 양수, 폴백 미진입.
  · `updateLocation()`(`:45-49`): `store.find(token)` 이 세션을 반환해야만 `save(copy)` 호출. find 는 만료 세션을
    **반환하지 않음**(Redis = native TTL 자동삭제로 키 부재, InMemory = lazy 비교, 30회차 확인) → save 에 전달되는
    `session.expiresAt` 은 항상 미래 → `ttlFrom` remaining 양수 → 음수/0 폴백 미진입.
  → 즉 `ttlFrom` 음수 경계 전용 테스트는 **30회차 InMemory CAS 분기(도달 불가 방어)와 동일 패턴**의
    억지 마이크로 가드(consensus 금지 busywork). 폴백 자체는 방어 코드로 유지(주석이 의도 문서화)하되 전용 테스트는 미추가.

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 150 그린(회귀 0).
- 코드 교차검증: `RedisLocationStore.kt`(91L) ↔ `RedisLocationStoreTest.kt`(5 test) ↔ `ShareService.kt`(create/update/get 흐름)
  1:1 정합. ttlFrom 음수 분기 도달성 = 도달 불가(find 가 만료 세션 미반환).
- 소스/테스트/문서 변경 0건(순수 검증). escalate/report-issue 미호출. 시크릿 비노출.

## 잔여·인계
- **39회차 RedisLocationStore 커버리지(봉인) + 38회차 라이브 smoke(drift 0) + 22~37회차 보안/계약/동시성/부팅 클래스 전부 봉인 — 미고정 갭 0**.
  다음 backend 사이클도 베이스라인 그린(150) 확인 후 **nothing-to-do 가 정상**(억지 마이크로 가드/리팩터/문서 재작성 금지).
  bueongi backend 13 사이클 연속 신규 작업 0건.
- **Frontend `78eb6275`(보호자 웹 지도, 80%) 지금 안전 연동 가능**: `docs/API.md` §`/share` 4종.
  경로 `/share`(not `/api`), POST(not PUT), DELETE 없음, GET location 항상 200+`expired`, TTL 1~3h.
  GET location 응답 = `lat`/`lng`/`updated_at`(snake_case)/`expired`.
- **신규 backend 태스크 등장 시에만 착수.** Redis 백엔드는 `BUEONGI_REDIS_URL` 주입 시에만 활성(미주입=InMemory 폴백);
  실 Redis 왕복 통합 테스트는 외부 인프라 필요 영역(현재는 단위 키-모델 봉인으로 충분).
