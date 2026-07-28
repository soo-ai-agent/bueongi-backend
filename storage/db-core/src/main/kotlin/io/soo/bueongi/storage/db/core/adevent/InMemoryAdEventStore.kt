package io.soo.bueongi.storage.db.core.adevent

import org.slf4j.LoggerFactory

/**
 * 인메모리 광고 노출 저장소(MVP 기본·유일 구현). 수신 요청 메트릭 저장소의 인메모리 폴백
 * ([io.soo.bueongi.storage.db.core.requestmetric.InMemoryRequestMetricStore])과 동일 철학 — 외부 인프라 없이
 * 동일 [AdEventStore] 계약으로 동작·테스트된다(재시작 시 휘발).
 *
 * 최신이 앞([addFirst])인 capped deque — 상한([AD_EVENT_CAP]) 초과 시 가장 오래된 것을 뒤에서 축출한다.
 */
class InMemoryAdEventStore : AdEventStore {

    private val logger = LoggerFactory.getLogger(InMemoryAdEventStore::class.java)
    private val events: ArrayDeque<AdEvent> = ArrayDeque<AdEvent>()
    private val lock: Any = Any()

    override fun record(event: AdEvent) {
        // 기록 실패가 실제 요청을 깨면 안 된다 — 삼키고 경고만(계약: record 는 예외를 밖으로 던지지 않는다).
        try {
            synchronized(lock) {
                events.addFirst(event)
                while (events.size > AD_EVENT_CAP) {
                    events.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("광고 노출 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<AdEvent> {
        val snapshot: List<AdEvent> = synchronized(lock) { events.toList() } // 이미 최신순(addFirst)
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
