package io.soo.bueongi.domain.admin

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

data class ProviderStatus(
    val provider: String,
    val total: Int,
    val successCount: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val lastSuccessAtMs: Long?,
    val lastFailureAtMs: Long?,
    val todayCount: Int,
    val quotaLimit: Int?,
    val quotaWarning: Boolean,
    val quotaBurnPerHour: Double?,
    val quotaProjectedTodayTotal: Int?,
    val hourlyCalls: List<HourlyBucket>,
)
