package io.soo.bueongi.admin

import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_CAP
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Service
class ApiStatusService(
    private val apiCallLogStore: ApiCallLogStore,
) {

    fun listRecentCalls(provider: String?, limit: Int): List<ApiCallLog> =
        apiCallLogStore.findRecent(provider = provider, limit = limit)

    fun summarize(): ApiStatusSummary {
        val now: Long = System.currentTimeMillis()
        val logs: List<ApiCallLog> = apiCallLogStore.findRecent(provider = null, limit = API_CALL_LOG_CAP)
        val todayStartMs: Long = todayStartMs(now)
        val byProvider: Map<String, List<ApiCallLog>> = logs.groupBy { it.provider }
        val providers: List<ProviderStatus> = TRACKED_PROVIDERS.map { provider ->
            aggregate(provider, byProvider[provider.name] ?: emptyList(), now, todayStartMs)
        }
        return ApiStatusSummary(
            providers = providers,
            windowSize = logs.size,
            hourlyTrend = hourlyBuckets(logs),
        )
    }

    private fun hourlyBuckets(logs: List<ApiCallLog>): List<HourlyBucket> =
        HourlyBuckets.countOf(logs.map { it.atEpochMs })

    private fun aggregate(provider: ApiProvider, logs: List<ApiCallLog>, nowMs: Long, todayStartMs: Long): ProviderStatus {
        val total: Int = logs.size
        val successCount: Int = logs.count { it.success }
        // 호출이 없으면 성공률은 정의되지 않는다 — 0.0 으로 내리면 "정상인데 성공률 0%"로 오독된다.
        val successRate: Double? = if (total == 0) {
            null
        } else {
            successCount.toDouble() / total
        }
        val avgDurationMs: Long = if (total == 0) {
            0L
        } else {
            (logs.sumOf { it.durationMs }.toDouble() / total).roundToLong()
        }
        val p95DurationMs: Long = Percentiles.nearestRank(logs.map { it.durationMs }.sorted(), 95)
        val todayCount: Int = logs.count { it.atEpochMs >= todayStartMs }
        val quotaLimit: Int? = QUOTA_LIMITS[provider]
        val quotaWarning: Boolean = quotaLimit != null && todayCount >= quotaLimit * QUOTA_WARNING_RATIO
        val quotaBurnPerHour: Double? = quotaBurnPerHour(quotaLimit, todayCount, nowMs, todayStartMs)
        return ProviderStatus(
            provider = provider.name,
            total = total,
            successCount = successCount,
            successRate = successRate,
            avgDurationMs = avgDurationMs,
            p95DurationMs = p95DurationMs,
            lastSuccessAtMs = logs.filter { it.success }.maxOfOrNull { it.atEpochMs },
            lastFailureAtMs = logs.filter { !it.success }.maxOfOrNull { it.atEpochMs },
            todayCount = todayCount,
            quotaLimit = quotaLimit,
            quotaWarning = quotaWarning,
            quotaBurnPerHour = quotaBurnPerHour,
            quotaProjectedTodayTotal = quotaProjectedTodayTotal(quotaBurnPerHour),
            hourlyCalls = hourlyBuckets(logs),
        )
    }

    private fun quotaBurnPerHour(quotaLimit: Int?, todayCount: Int, nowMs: Long, todayStartMs: Long): Double? {
        if (quotaLimit == null) {
            return null
        }
        val elapsedHoursToday: Double = (nowMs - todayStartMs).toDouble() / HOUR_MS
        if (elapsedHoursToday <= 0.0) {
            return 0.0
        }
        return todayCount / elapsedHoursToday
    }

    private fun quotaProjectedTodayTotal(burnPerHour: Double?): Int? {
        if (burnPerHour == null) {
            return null
        }
        return (burnPerHour * HOURS_PER_DAY).roundToInt()
    }

    private fun todayStartMs(nowMs: Long): Long {
        val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    companion object {
        /** [ApiProvider] 전체를 추적한다 — 원천이 늘어도 화면에서 빠지지 않게 enum 을 진실로 삼는다. */
        val TRACKED_PROVIDERS: List<ApiProvider> = ApiProvider.entries

        val QUOTA_LIMITS: Map<ApiProvider, Int> = mapOf(ApiProvider.DATAGOKR to 1000)

        const val QUOTA_WARNING_RATIO: Double = 0.8

        const val HOUR_MS: Long = 3_600_000L

        const val HOURS_PER_DAY: Int = 24

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
