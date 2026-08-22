package io.soo.bueongi.storage.db.core.externalcall

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryExternalCallMetricStore : ExternalCallMetricStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryExternalCallMetricStore::class.java)
    private val metrics: ArrayDeque<ExternalCallMetric> = ArrayDeque<ExternalCallMetric>()
    private val lock = Any()

    override fun record(metric: ExternalCallMetric) {
        try {
            synchronized(lock) {
                metrics.addFirst(metric)
                while (metrics.size > EXTERNAL_CALL_METRIC_CAP) {
                    metrics.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("외부 API 호출 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<ExternalCallMetric> {
        val snapshot: List<ExternalCallMetric> = synchronized(lock) { metrics.toList() }
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
