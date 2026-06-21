# 위치 공유 서버 — 인메모리 구현 (backend 19회차)

- 일시: 2026-06-19 05:04 KST
- 역할/엔진: bueongi-backend / Claude(opus)
- 칸반: `cdb5314d [Backend/Server] Redis 기반 위치 공유 최소 서버` (20% → 70%)

## 요청
자동 루프 backend 사이클. 직전(18회차)에 위치 공유 서버 **설계 제안서**(`docs/LOCATION_SHARING_DESIGN.md`)를 작성하고
"PM 비준 + Redis 인프라 승인 + 스코프 결정" 게이트 대기 상태로 인계. 이번 사이클은 게이트 상태를 재확인하고 다음 가치를 결정.

## 진행
1. 상태 복원: consensus.md(18회차)·handoff.md(18회차)·에이전트 정의(`bueongi-backend.md`)·설계서 정독.
2. 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, **tests=108/0/0/0**(클린). 깨진 빌드/테스트 없음.
3. 칸반 점검(`list_tasks` 24건): `safe-return-mvp` 에픽 6태스크 전부 backlog. PM 설계(`03b7bcf7`) 0% 미착수 → **PM 비준 게이트 미해소**.
4. **핵심 판단**: 게이트 3개 중 **인메모리 구현은 인프라 승인이 불필요**하다. 설계서 §4·§5 의 `LocationStore` 추상화는
   Redis 미주입 시 인메모리 폴백이 기본(장소 `PlaceProvider`/`SeedPlaceProvider` 와 동일 철학)이고, 설계서 §9-2/§9-3 이
   "인메모리 스토어 먼저 구현 → 테스트 그린 → Redis 추가", "인메모리로 frontend 78eb6275 폴링 연동 병행 가능"을 명시.
   Redis starter 의존성(§8 승인 대상)을 추가하지 않으면 비용/인프라 결정과 무관하게 **순수 코드 가산**으로 진척 가능 →
   17사이클 포화를 실질적으로 탈출하는 다음 단계. 따라서 **인메모리 위치 공유 서버를 구현**.

## 산출물
**신규 main 소스 6 + config 1 + 기존 2 수정:**
- `share/LocationStore.kt`(인터페이스 + `ShareSession`/`StoredLocation` 도메인 모델)
- `share/InMemoryLocationStore.kt`(@Component, `ConcurrentHashMap` + Clock 기반 세션 만료 lazy 제거 = Redis EXPIRE 동치)
- `share/ShareDtos.kt`(요청/응답 4종 + TTL 상수: 세션 기본 6h/하한 60s/상한 12h, 위치 stale 90s)
- `share/ShareSessionService.kt`(@Service: 세션 생명주기 + 128-bit URL-safe 랜덤 토큰 + stale 계산, 주입 Clock)
- `share/ShareController.kt`(@RestController, `/api/share/sessions` 4 엔드포인트)
- `config/ClockConfig.kt`(주입형 `Clock` 빈 — TTL/stale 결정론 테스트용)
- `common/ApiExceptionHandler.kt` 수정: `ShareSessionNotFoundException` + 404 `SHARE_SESSION_NOT_FOUND` 핸들러(공통 스키마 재사용)
- `health/HealthController.kt` 수정: `HealthStatus.locationStore`("memory"|"redis") 라벨 추가(placeSource 패턴, 자격증명 비노출)

**API 계약(설계서 §3 그대로 구현):**
- `POST /api/share/sessions` → 200 `{shareId, expiresAt, updateUrl, viewUrl}`(ttlSeconds 선택)
- `PUT /api/share/sessions/{shareId}/location` → 200 `{shareId, updatedAt, expiresAt}`(좌표 검증 재사용)
- `GET /api/share/sessions/{shareId}` → 200(위치+stale) / 204(첫 위치 전) / 404(없음·만료)
- `DELETE /api/share/sessions/{shareId}` → 204(멱등)

**테스트 31건 신규**: `InMemoryLocationStoreTest`(6: 왕복·만료 lazy 제거·멱등·덮어쓰기), `ShareSessionServiceTest`(13:
TTL·기본값·토큰 유일성/길이·stale 경계·리셋·만료 404·격리), `ShareControllerTest`(12: HTTP 200/204/404·400 VALIDATION/MALFORMED·멱등 DELETE). `HealthControllerTest` 갱신(locationStore="memory" 단언 + 스텁 빈).

`docs/LOCATION_SHARING_DESIGN.md` 상태 헤더를 "인메모리 구현 완료, Redis는 승인 후"로 갱신(구현 현황 체크리스트 추가).

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, **tests=139/skipped=0/failures=0/errors=0**(108 → +31, 클린 5 executed).
- 좌표 규약·에러 계약·CORS·health 라벨 패턴 전면 재사용(기존 엔드포인트 거동 완전 불변 — 순수 가산).
- 시크릿 비노출: shareId 는 랜덤 토큰(PII 미저장), health 는 저장소 종류 라벨만(Redis URL/자격증명 미노출).
- Redis 의존성 미추가(§8 승인 게이트 준수). projectPath 밖 변경 없음. 커밋/푸시 없음.

## 잔여 · 인계
1. **PM 비준 + Redis 인프라 승인**(설계서 §8) 내려지면: `RedisLocationStore` 를 동일 `LocationStore` 슬롯에 추가
   (`spring-boot-starter-data-redis` 의존성 + `BUEONGI_REDIS_URL` 배선 + isEnabled 런타임 게이트). 인메모리 계약/테스트는 그대로 회귀 가드.
2. **frontend(`78eb6275`)**: 위 4종 계약으로 보호자 웹 지도 폴링 연동을 **지금 병행 가능**(인메모리로 백엔드 기동, Redis 불요).
   `GET`의 204(첫 위치 전)·404(만료)·`stale` 플래그 분기 주의.
3. escalate/report-issue 미호출: Redis 인프라 승인은 PM 비준과 함께 올라가는 게 맞고(현 단계는 막힘 아님), 새 버그/실패 없음.
