package io.soo.bueongi.core.ops.domain.external.model

import io.soo.bueongi.support.util.HourlyBucket

data class ExternalApiSummary(
    val totalCalls: Int,
    val failureCount: Int,
    val callsToday: Int,
    val callsLastHour: Int,
    /** 보관 상한 — 이 수에 닿았으면 오래된 기록이 밀려나 실제 누적은 더 많다. */
    val windowCap: Int,
    val hourlyCalls: List<HourlyBucket>,
    val byProvider: List<ExternalApiUsage>,
)
