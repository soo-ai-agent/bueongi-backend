package io.soo.bueongi.core.ops.domain.apistatus

import io.soo.bueongi.support.util.HourlyBucket

data class ApiStatusSummary(
    val providers: List<ProviderStatus>,
    val windowSize: Int,
    val hourlyTrend: List<HourlyBucket>,
)
