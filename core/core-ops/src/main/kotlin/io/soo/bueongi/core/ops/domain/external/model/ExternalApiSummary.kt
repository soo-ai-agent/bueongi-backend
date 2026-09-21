package io.soo.bueongi.core.ops.domain.external.model

import io.soo.bueongi.support.util.HourlyBucket

data class ExternalApiSummary(
    val totalCalls: Int,
    val failureCount: Int,
    val callsToday: Int,
    val callsLastHour: Int,
    val windowCap: Int,
    val hourlyCalls: List<HourlyBucket>,
    val byProvider: List<ExternalApiUsage>,
)
