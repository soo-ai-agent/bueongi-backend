package io.soo.bueongi.storage.db.core.requestmetric

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Redis 미주입(로컬/CI/MVP 기본) 시 동작하는 인메모리 수신 요청 메트릭 저장소. 접속 내역/추천 통계 저장소의
 * 인메모리 폴백과 동일 철학 — 외부 인프라 없이 동일 [RequestMetricStore] 계약으로 동작·테스트된다(재시작 시 휘발).
 *
 * 최신이 앞([addFirst])인 capped deque — 상한([REQUEST_METRIC_CAP]) 초과 시 가장 오래된 것을 뒤에서 축출한다.
 */
class InMemoryRequestMetricStore : RequestMetricStore {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryRequestMetricStore::class.java)
    private val metrics: ArrayDeque<RequestMetric> = ArrayDeque<RequestMetric>()
    private val lock: Any = Any()

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
