package io.soo.bueongi.core.domain.admin.traffic

import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.support.util.HourlyBuckets
import io.soo.bueongi.core.domain.admin.apistatus.LatencyPercentiles
import io.soo.bueongi.support.util.Percentiles

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

@Service
class RequestMetricsService(
    private val requestMetricStore: RequestMetricStore,
) {

    fun summarize(): TrafficSummary {
        val metrics: List<RequestMetric> = requestMetricStore.findRecent(REQUEST_METRIC_CAP)
        return TrafficSummary(
            totalCalls = metrics.size,
            windowSize = metrics.size,
            byEndpoint = byEndpoint(metrics),
            estimatedUsers = estimatedUsers(metrics),
            latency = latency(metrics),
            hourlyCalls = hourlyCalls(metrics),
            hourlyErrors = hourlyErrors(metrics),
            dailyActiveUsers = dailyActiveUsers(metrics),
        )
    }

    private fun latency(metrics: List<RequestMetric>): LatencyPercentiles {
        val sortedDurations: List<Long> = metrics.map { it.durationMs }.sorted()
        return LatencyPercentiles(
            p50Ms = Percentiles.nearestRank(sortedDurations, 50),
            p95Ms = Percentiles.nearestRank(sortedDurations, 95),
            p99Ms = Percentiles.nearestRank(sortedDurations, 99),
        )
    }

    private fun byEndpoint(metrics: List<RequestMetric>): List<EndpointTraffic> =
        metrics.groupBy { EndpointKey(method = it.method, endpoint = it.endpoint) }
            .map { (key, group) -> aggregateEndpoint(key, group) }
            .sortedByDescending { it.count }

    private fun aggregateEndpoint(key: EndpointKey, group: List<RequestMetric>): EndpointTraffic {
        val count: Int = group.size
        val successCount: Int = group.count { it.status in SUCCESS_STATUS_RANGE }
        val successRate: Double = successCount.toDouble() / count
        val avgDurationMs: Long = (group.sumOf { it.durationMs }.toDouble() / count).roundToLong()
        val p95DurationMs: Long = Percentiles.nearestRank(group.map { it.durationMs }.sorted(), 95)
        return EndpointTraffic(
            method = key.method,
            endpoint = key.endpoint,
            count = count,
            successRate = successRate,
            avgDurationMs = avgDurationMs,
            p95DurationMs = p95DurationMs,
            distinctClients = distinctClients(group),
        )
    }

    private fun estimatedUsers(metrics: List<RequestMetric>): EstimatedUsers {
        val now: Long = System.currentTimeMillis()
        val todayStartMs: Long = todayStartMs(now)
        val newVsReturning: NewVsReturning = newVsReturningToday(metrics, todayStartMs)
        return EstimatedUsers(
            today = distinctClientsSince(metrics, todayStartMs),
            last7d = distinctClientsSince(metrics, now - DAYS_7_MS),
            last30d = distinctClientsSince(metrics, now - DAYS_30_MS),
            callsWithoutClientId = metrics.count { it.clientId == null },
            newToday = newVsReturning.newToday,
            returningToday = newVsReturning.returningToday,
        )
    }

    private fun newVsReturningToday(metrics: List<RequestMetric>, todayStartMs: Long): NewVsReturning {
        val firstSeenByClient: Map<String, Long> = firstSeenByClient(metrics)
        val activeTodayClients: List<String> = metrics
            .filter { it.atEpochMs >= todayStartMs }
            .mapNotNull { it.clientId }
            .distinct()
        val newCount: Int = activeTodayClients.count { clientId ->
            val firstSeen: Long = firstSeenByClient[clientId] ?: todayStartMs
            firstSeen >= todayStartMs
        }
        return NewVsReturning(newToday = newCount, returningToday = activeTodayClients.size - newCount)
    }

    private fun firstSeenByClient(metrics: List<RequestMetric>): Map<String, Long> {
        val firstSeen: MutableMap<String, Long> = mutableMapOf<String, Long>()
        metrics.forEach { metric ->
            val clientId: String = metric.clientId ?: return@forEach
            val previous: Long? = firstSeen[clientId]
            if (previous == null || metric.atEpochMs < previous) {
                firstSeen[clientId] = metric.atEpochMs
            }
        }
        return firstSeen
    }

    private fun distinctClientsSince(metrics: List<RequestMetric>, sinceMs: Long): Int =
        distinctClients(metrics.filter { it.atEpochMs >= sinceMs })

    private fun distinctClients(metrics: List<RequestMetric>): Int =
        metrics.mapNotNull { it.clientId }.distinct().size

    private fun hourlyCalls(metrics: List<RequestMetric>): List<HourlyBucket> =
        HourlyBuckets.countOf(metrics.map { it.atEpochMs })

    private fun hourlyErrors(metrics: List<RequestMetric>): List<HourlyBucket> {
        val errorTimestampsMs: List<Long> = metrics
            .filter { it.status !in SUCCESS_STATUS_RANGE }
            .map { it.atEpochMs }
        return HourlyBuckets.countOf(errorTimestampsMs)
    }

    private fun dailyActiveUsers(metrics: List<RequestMetric>): List<DailyActive> {
        val today: LocalDate = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(SEOUL).toLocalDate()
        return (DAILY_ACTIVE_DAYS - 1 downTo 0).map { daysAgo ->
            val dayStart: ZonedDateTime = today.minusDays(daysAgo.toLong()).atStartOfDay(SEOUL)
            val startMs: Long = dayStart.toInstant().toEpochMilli()
            val endMs: Long = dayStart.plusDays(1).toInstant().toEpochMilli()
            val ofDay: List<RequestMetric> = metrics.filter { it.atEpochMs >= startMs && it.atEpochMs < endMs }
            DailyActive(dateEpochMs = startMs, distinctClients = distinctClients(ofDay))
        }
    }

    private fun todayStartMs(nowMs: Long): Long {
        val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    private data class EndpointKey(
        val method: String,
        val endpoint: String,
    )

    private data class NewVsReturning(
        val newToday: Int,
        val returningToday: Int,
    )

    companion object {
        val SUCCESS_STATUS_RANGE: IntRange = 200..399

        const val DAILY_ACTIVE_DAYS: Int = 7

        const val DAYS_7_MS: Long = 7L * 24 * 60 * 60 * 1000

        const val DAYS_30_MS: Long = 30L * 24 * 60 * 60 * 1000

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
