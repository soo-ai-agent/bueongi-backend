package io.soo.bueongi.storage.db.core.requestmetric

import io.soo.bueongi.storage.db.core.InMemoryRecentLog

class InMemoryRequestMetricStore(cap: Int = REQUEST_METRIC_CAP) : RequestMetricStore {
    private val recent: InMemoryRecentLog<RequestMetric> = InMemoryRecentLog(cap)

    override fun record(metric: RequestMetric) {
        recent.push(metric)
    }

    override fun findRecent(limit: Int): List<RequestMetric> = recent.readRecent(limit)
}
