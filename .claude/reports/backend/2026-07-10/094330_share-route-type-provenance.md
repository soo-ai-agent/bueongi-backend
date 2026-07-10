# 공유 route 계약 확장 — type·provenance (보호자 정보 패리티)

## 1. 요청 (Request)
- 사람 승인 완료된 additive·하위호환 계약 변경. 공유 세션 route 등록/조회 흐름에 optional 필드 2개 추가:
  - `type`: string enum `safe|fast|main`
  - `provenance`: string(출처·기준일 문구)
- 인수 조건: (1) POST route 요청 바디에 두 필드 추가, (2) GET route 응답에 그대로 에코, (3) 미전송 시 기존 동작 완전 동일(null), (4) type enum 위반 422 / provenance 200자 초과 422, 기존 에러 포맷 준수.
- 검증: 정상 1 + 에러 2 + 하위호환 1 테스트, `./gradlew test` BUILD SUCCESSFUL. 재기동·실 API 스모크는 오케스트레이터가 수행(스모크 curl만 반환).

## 2. 진행 (Progress)
- 대상 엔드포인트 확정: `POST /share/{token}/route`(SaveRouteRequest → StoredRoute), `GET /share/{token}/route`(RouteView.route = RouteContext). `/api` 아닌 `/share` 계열이 맞음.
- 에러 상태코드 결정: 코드베이스의 @Valid 실패는 400 VALIDATION_FAILED(ProblemDetail)로 매핑되고, 422는 **경계 변환 계층이 도메인 예외를 던져** ApiControllerAdvice가 매핑하는 패턴(RouteRequest.toValidatedQuery → ORIGIN_REQUIRED/SAME_ORIGIN/TRIP_TOO_FAR)이 이미 존재. 계약이 요구한 422 + "RouteQuery 경계 검증 패턴 준수" 지침이 이 패턴을 정확히 가리키므로, 신규 필드 검증을 `SaveRouteRequest.toStoredRoute()` 경계에서 수행하고 `InvalidRouteMetaException`(422 `INVALID_ROUTE_META`)를 던지도록 구현. 라우터(컨트롤러)는 얇게 유지(수동 검증 없음).
- 주의점(인계 참고): 같은 요청의 마커 `type`은 @Pattern → 400 VALIDATION_FAILED인데, 신규 route `type`은 계약 지시대로 422로 처리. 즉 동일 엔드포인트 내 두 enum 위반의 상태코드가 다름(400 vs 422). 계약이 명시적으로 422를 요구했고 코드베이스에 422 경계-검증 선례가 있어 그대로 따름. PM/QA가 일관성 관점에서 재검토할 수 있음.
- 하위호환: StoredRoute에 nullable 기본값 필드로 추가 → InMemory는 객체 그대로 보관(무영향), Redis는 Jackson JSON 직렬화라 기존 저장분(필드 없음)은 Kotlin 기본값 null로 역직렬화됨(RedisLocationStore.find의 readValue). 별도 마이그레이션 불필요.
- 막힘: 첫 `./gradlew test`가 stale 증분 컴파일 캐시로 Kotlin 컴파일러 내부오류(NoSuchFileException: StoredLocation.class). `./gradlew clean test`로 해소 → BUILD SUCCESSFUL.
- 엔진: claude(claude-opus), fallback 미사용.

## 3. 산출물 (Artifacts)
변경 파일(모두 backend/ 내부):
- `storage/db-core/src/main/kotlin/io/soo/bueongi/storage/db/core/share/LocationStore.kt` — `StoredRoute`에 `type: String? = null`, `provenance: String? = null` 추가.
- `core/core-api/src/main/kotlin/io/soo/bueongi/domain/share/ShareResults.kt` — 상수 `ROUTE_TYPE_VALUES = {safe,fast,main}`, `MAX_ROUTE_PROVENANCE_LENGTH = 200` 추가; `RouteContext`에 `type`/`provenance` 추가; `StoredRoute.toRouteContext()` 매핑에 두 필드 에코.
- `core/core-api/src/main/kotlin/io/soo/bueongi/support/error/CoreExceptions.kt` — `InvalidRouteMetaException(field, message, rejectedValue)` 추가.
- `core/core-api/src/main/kotlin/io/soo/bueongi/api/ApiControllerAdvice.kt` — `@ExceptionHandler(InvalidRouteMetaException)` → 422 `INVALID_ROUTE_META` + `errors[{field,message,rejectedValue}]`.
- `core/core-api/src/main/kotlin/io/soo/bueongi/api/v1/request/ShareRequests.kt` — `SaveRouteRequest`에 `type`/`provenance` 필드; `toStoredRoute()`가 `validateRouteMeta()`(경계 검증) 호출 후 두 값을 StoredRoute에 매핑.
- `core/core-api/src/test/kotlin/io/soo/bueongi/api/v1/ShareControllerTest.kt` — 테스트 4건 추가.

계약(plan.md) 변경: 아래 "계약 명세"를 plan.md OpenAPI에 반영 필요(PM). 마이그레이션: 없음(스키마리스 Redis/인메모리, additive nullable). 커밋: 없음(오케스트레이터가 수행).

### 계약 명세 (필드명 그대로)
- `POST /share/{token}/route` 요청 바디 추가(둘 다 optional):
  - `type`: string, enum `["safe","fast","main"]`
  - `provenance`: string, maxLength 200
  - 위반 시 422 `{ "code": "INVALID_ROUTE_META", "errors": [{ "field": "type"|"provenance", "message": ..., "rejectedValue": ... }], ... }`(RFC 7807 ProblemDetail + code). type의 rejectedValue=거부값, provenance의 rejectedValue=입력 길이.
- `GET /share/{token}/route` 응답의 `route` 객체에 추가(에코, 미저장 시 null):
  - `route.type`: string|null
  - `route.provenance`: string|null

## 4. 검증 (Verification)
- 명령: `set -a; . ./.env.local; set +a; ./gradlew clean test` → **BUILD SUCCESSFUL** (1m 30s).
- ShareControllerTest: tests=38, failures=0, errors=0, skipped=0. 신규 4건 모두 통과:
  - `type·provenance 포함 POST route 후 GET route 는 두 값을 그대로 에코` (정상)
  - `type·provenance 생략 시 GET route 는 두 값이 null(하위호환)` (하위호환)
  - `허용되지 않은 route type 은 422 INVALID_ROUTE_META` (에러 1)
  - `길이 초과 provenance 는 422 INVALID_ROUTE_META` (에러 2)
- 미검증: dev 서버 재기동 후 실 API 스모크는 지침대로 수행하지 않음(오케스트레이터가 프론트 연결 후 일괄). Redis 경로의 하위호환 역직렬화는 단위 테스트(InMemory)로만 확인 — 실 Redis 왕복은 미검증(현 로컬 기본은 인메모리).

### 스모크 curl (오케스트레이터용, dev :8119 기동 후)
```bash
# 0) 공유 생성 → token, owner_secret 추출
RESP=$(curl -s -X POST http://localhost:8119/share/create -H 'Content-Type: application/json' -d '{}')
TOKEN=$(echo "$RESP" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
SECRET=$(echo "$RESP" | sed -n 's/.*"owner_secret":"\([^"]*\)".*/\1/p')

# 1) 정상: type+provenance 저장 → 200
curl -s -X POST http://localhost:8119/share/$TOKEN/route -H 'Content-Type: application/json' -d "{\"owner_secret\":\"$SECRET\",\"destination\":{\"lat\":37.56,\"lng\":126.97},\"type\":\"safe\",\"provenance\":\"서울시 안심이 CCTV 2026-03 기준\"}"

# 2) 에코 확인: route.type/route.provenance 포함 → 200
curl -s http://localhost:8119/share/$TOKEN/route

# 3) 에러: enum 위반 → 422 INVALID_ROUTE_META
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8119/share/$TOKEN/route -H 'Content-Type: application/json' -d "{\"owner_secret\":\"$SECRET\",\"destination\":{\"lat\":37.56,\"lng\":126.97},\"type\":\"scenic\"}"

# 4) 하위호환: 두 필드 생략 → 200, route.type/provenance = null
curl -s -X POST http://localhost:8119/share/$TOKEN/route -H 'Content-Type: application/json' -d "{\"owner_secret\":\"$SECRET\",\"destination\":{\"lat\":37.56,\"lng\":126.97}}" && curl -s http://localhost:8119/share/$TOKEN/route
```

## 5. 잔여·인계 (Handoff)
- 프론트(bueongi-dev): GET route 응답의 `route.type`/`route.provenance`를 보호자 화면에 표시. 공유자 측 POST route에 두 필드 실어 보내면 됨(생략 무해).
- PM: plan.md OpenAPI에 위 "계약 명세" 반영. 상태코드 일관성 결정 필요 — 마커 `type`(@Pattern) 400 vs route `type` 422가 같은 엔드포인트에 공존. 계약이 route 필드에 422를 명시해 그대로 구현했으나, 통일 원할 경우 후속 카드로 조정 가능.
- QA: 위 스모크 curl로 경계면 교차검증(요청/응답 필드명·상태코드). Redis 배선 시 기존 저장분 하위호환(구 JSON → null) 실환경 확인 권장.
- 사용자 게이트: 없음(파괴적 변경·마이그레이션·secret 무관). 커밋/머지는 오케스트레이터.
- 리스크: 낮음. additive nullable, 기존 테스트 34건 + 신규 4건 전부 통과. backend/ 밖 변경 없음, git 명령 미수행.

---
meta: { role: backend, engine: claude, cycle: 1, taskId: share-route-type-provenance, date: 2026-07-10T09:43:30+09:00 }
