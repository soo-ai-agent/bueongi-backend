package io.soo.bueongi.core.ops.domain.external.service

import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.core.ops.domain.external.model.ExternalApiEndpoint
import io.soo.bueongi.core.ops.domain.external.model.ExternalApiSummary
import io.soo.bueongi.core.ops.domain.external.model.ExternalApiUsage
import io.soo.bueongi.storage.db.core.externalcall.EXTERNAL_CALL_METRIC_CAP
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetric
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.support.util.countByHour
import io.soo.bueongi.support.util.nearestRankPercentile
import io.soo.bueongi.support.util.todayStartMs
import org.springframework.stereotype.Service
import java.time.Clock
import kotlin.math.roundToLong

@Service
class ExternalApiMetricsService(
    private val externalCallMetricStore: ExternalCallMetricStore,
    private val clock: Clock,
) {

    fun summarize(): ExternalApiSummary {
        val metrics: List<ExternalCallMetric> = externalCallMetricStore.findRecent(EXTERNAL_CALL_METRIC_CAP)
        val nowMs: Long = clock.millis()
        return ExternalApiSummary(
            totalCalls = metrics.size,
            failureCount = metrics.count { isFailure(it) },
            callsToday = countSince(metrics, todayStartMs(nowMs)),
            callsLastHour = countSince(metrics, nowMs - ONE_HOUR_MS),
            windowCap = EXTERNAL_CALL_METRIC_CAP,
            hourlyCalls = countByHour(metrics.map { it.atEpochMs }),
            byProvider = byProvider(metrics, nowMs),
        )
    }

    private fun byProvider(metrics: List<ExternalCallMetric>, nowMs: Long): List<ExternalApiUsage> {
        val usages: MutableList<ExternalApiUsage> = mutableListOf()
        for ((provider, group) in metrics.groupBy { it.provider }) {
            usages.add(aggregateProvider(provider, group, nowMs))
        }
        usages.sortByDescending { it.totalCalls }
        return usages
    }

    private fun aggregateProvider(
        provider: ExternalApiProvider,
        group: List<ExternalCallMetric>,
        nowMs: Long,
    ): ExternalApiUsage {
        val total: Int = group.size
        val failures: List<ExternalCallMetric> = group.filter { isFailure(it) }
        val durations: List<Long> = group.map { it.durationMs }.sorted()
        val lastFailure: ExternalCallMetric? = failures.maxByOrNull { it.atEpochMs }
        val lastCall: ExternalCallMetric? = group.maxByOrNull { it.atEpochMs }
        return ExternalApiUsage(
            provider = provider,
            totalCalls = total,
            failureCount = failures.size,
            successRate = (total - failures.size).toDouble() / total,
            avgDurationMs = (group.sumOf { it.durationMs }.toDouble() / total).roundToLong(),
            p95DurationMs = nearestRankPercentile(durations, 95),
            callsToday = countSince(group, todayStartMs(nowMs)),
            callsLastHour = countSince(group, nowMs - ONE_HOUR_MS),
            lastCallAtEpochMs = lastCall?.atEpochMs,
            lastFailureReason = lastFailure?.let { reasonOf(it) },
            lastFailureAtEpochMs = lastFailure?.atEpochMs,
            lastCallOk = lastCall == null || !isFailure(lastCall),
            hourlyCalls = countByHour(group.map { it.atEpochMs }),
            hourlyFailures = countByHour(failures.map { it.atEpochMs }),
            topEndpoints = topEndpoints(group),
        )
    }

    private fun reasonOf(metric: ExternalCallMetric): String {
        val reason: String? = metric.failureReason
        if (reason != null) {
            return reason
        }
        return "HTTP ${metric.status}"
    }

    private fun topEndpoints(group: List<ExternalCallMetric>): List<ExternalApiEndpoint> {
        val endpoints: MutableList<ExternalApiEndpoint> = mutableListOf()
        for ((endpoint, calls) in group.groupBy { it.endpoint }) {
            endpoints.add(
                ExternalApiEndpoint(
                    endpoint = endpoint,
                    count = calls.size,
                    failureCount = calls.count { isFailure(it) },
                    avgDurationMs = (calls.sumOf { it.durationMs }.toDouble() / calls.size).roundToLong(),
                ),
            )
        }
        endpoints.sortByDescending { it.count }
        return endpoints.take(TOP_ENDPOINT_LIMIT)
    }

    private fun countSince(metrics: List<ExternalCallMetric>, sinceMs: Long): Int =
        metrics.count { it.atEpochMs >= sinceMs }

    private fun isFailure(metric: ExternalCallMetric): Boolean =
        metric.status !in SUCCESS_STATUS_RANGE

    private companion object {
        val SUCCESS_STATUS_RANGE: IntRange = 200..299
        const val ONE_HOUR_MS: Long = 60 * 60 * 1000L
        const val TOP_ENDPOINT_LIMIT: Int = 5
    }
}
