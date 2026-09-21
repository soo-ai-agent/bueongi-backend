package io.soo.bueongi.core.ops.domain.external.model

import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.support.util.HourlyBucket

data class ExternalApiUsage(
    val provider: ExternalApiProvider,
    val totalCalls: Int,
    val failureCount: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val callsToday: Int,
    val callsLastHour: Int,
    val lastCallAtEpochMs: Long?,
    val lastFailureReason: String?,
    // 호출이 없었으면 true.
    val lastCallOk: Boolean,
    val hourlyCalls: List<HourlyBucket>,
    val hourlyFailures: List<HourlyBucket>,
    val topEndpoints: List<ExternalApiEndpoint>,
)
