package io.soo.bueongi.storage.db.core.requestmetric

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class InMemoryRequestMetricStore : RequestMetricStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryRequestMetricStore::class.java)
    private val metrics: ArrayDeque<RequestMetric> = ArrayDeque<RequestMetric>()
    private val lock = Any()

    override fun record(metric: RequestMetric) {
        try {
            synchronized(lock) {
                metrics.addFirst(metric)
                while (metrics.size > REQUEST_METRIC_CAP) {
                    metrics.removeLast()
                }
            }
        } catch (e: Exception) {
            logger.warn("수신 요청 메트릭 기록 실패(무시): {}", e.message)
        }
    }

    override fun findRecent(limit: Int): List<RequestMetric> {
        val snapshot: List<RequestMetric> = synchronized(lock) { metrics.toList() }
        return snapshot.take(limit.coerceAtLeast(0))
    }
}
