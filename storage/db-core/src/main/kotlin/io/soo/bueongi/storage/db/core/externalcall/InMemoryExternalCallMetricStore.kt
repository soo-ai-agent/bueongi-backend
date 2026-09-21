package io.soo.bueongi.storage.db.core.externalcall

import io.soo.bueongi.storage.db.core.InMemoryRecentLog

class InMemoryExternalCallMetricStore : ExternalCallMetricStore {
    private val recent: InMemoryRecentLog<ExternalCallMetric> = InMemoryRecentLog(EXTERNAL_CALL_METRIC_CAP)

    override fun record(metric: ExternalCallMetric) {
        recent.push(metric)
    }

    override fun findRecent(limit: Int): List<ExternalCallMetric> = recent.readRecent(limit)
}
