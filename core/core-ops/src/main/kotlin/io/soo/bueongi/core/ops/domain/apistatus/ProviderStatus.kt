package io.soo.bueongi.core.ops.domain.apistatus

import io.soo.bueongi.support.util.HourlyBucket

data class ProviderStatus(
    val provider: String,
    val total: Int,
    val successCount: Int,
    /** 0~1. 호출 0건이면 null(미정의) — 전부 실패한 0.0 과 구분한다. */
    val successRate: Double?,
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
