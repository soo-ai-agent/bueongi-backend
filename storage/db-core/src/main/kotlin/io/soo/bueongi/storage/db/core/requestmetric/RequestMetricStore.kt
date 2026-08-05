package io.soo.bueongi.storage.db.core.requestmetric


/**
 * 수신 요청 메트릭 저장소. 외부 API 접속 내역([io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore])·
 * 추천 통계([io.soo.bueongi.storage.db.core.routestats.RouteStatsStore])와 동일 철학 —
 * Redis 주입 시 [RedisRequestMetricStore], 미주입(로컬/CI/MVP)이면 [InMemoryRequestMetricStore] 가 동일 계약으로 폴백한다.
 *
 * 최근 [REQUEST_METRIC_CAP] 건만 유지하는 순환 버퍼다(오래된 것은 축출). 기록은 요청 처리의 순수 부수효과이므로,
 * [record] 는 어떤 경우에도 예외를 밖으로 던지지 않는다(계측 실패가 실제 요청을 깨면 안 된다 — 내부에서 삼키고 로깅만).
 */
interface RequestMetricStore {
    /** 계측 1건 저장. **절대 예외를 던지지 않는다**(내부 try/catch 로 삼킨다). */
    fun record(metric: RequestMetric)

    /** 최근순(최신 먼저) 조회. 최대 [limit] 건. */
    fun findRecent(limit: Int): List<RequestMetric>
}

/** 순환 버퍼 상한 — 최근 이만큼만 유지한다(Redis LTRIM / 인메모리 deque 축출). 전량 수신 트래픽이라 넉넉히 잡는다. */
const val REQUEST_METRIC_CAP: Int = 5000
