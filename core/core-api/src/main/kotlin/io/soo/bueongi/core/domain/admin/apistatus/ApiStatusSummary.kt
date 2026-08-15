package io.soo.bueongi.core.domain.admin.apistatus

import io.soo.bueongi.core.domain.admin.HourlyBucket

data class ApiStatusSummary(
    val providers: List<ProviderStatus>,
    val windowSize: Int,
    val hourlyTrend: List<HourlyBucket>,
)
