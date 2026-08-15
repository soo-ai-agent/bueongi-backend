package io.soo.bueongi.core.domain.admin.adstats

import io.soo.bueongi.support.util.HourlyBucket

data class AdStatsSummary(
    val total: Int,
    val today: Int,
    val byPlacement: Map<String, Int>,
    val hourlyTrend: List<HourlyBucket>,
)
