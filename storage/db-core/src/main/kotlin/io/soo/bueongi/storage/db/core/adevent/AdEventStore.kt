package io.soo.bueongi.storage.db.core.adevent


/**
 * 광고 노출 이벤트 저장소. 수신 요청 메트릭 저장소([io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore])와
 * 동일 철학이다 — RequestMetricStore 는 Redis 주입 시 Redis 구현으로, 미주입이면 인메모리로 폴백하지만,
 * 광고 노출은 이번 범위에서 **인메모리([InMemoryAdEventStore])만** 제공한다(MVP). Redis 확장이 필요해지면
 * RequestMetricStore 처럼 `bueongi.share.redis-url` 유무로 빈을 가르는 구현을 추가하면 된다.
 *
 * 최근 [AD_EVENT_CAP] 건만 유지하는 순환 버퍼다(오래된 것은 축출). 기록은 요청 처리의 순수 부수효과이므로,
 * [record] 는 어떤 경우에도 예외를 밖으로 던지지 않는다(계측 실패가 실제 요청을 깨면 안 된다 — 내부에서 삼키고 로깅만).
 */
interface AdEventStore {
    /** 노출 1건 저장. **절대 예외를 던지지 않는다**(내부 try/catch 로 삼킨다). */
    fun record(event: AdEvent)

    /** 최근순(최신 먼저) 조회. 최대 [limit] 건. */
    fun findRecent(limit: Int): List<AdEvent>
}

/** 순환 버퍼 상한 — 최근 이만큼만 유지한다(인메모리 deque 축출). */
const val AD_EVENT_CAP: Int = 5000
