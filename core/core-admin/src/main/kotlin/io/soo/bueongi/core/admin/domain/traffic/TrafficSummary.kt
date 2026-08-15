package io.soo.bueongi.core.admin.domain.traffic

import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.core.admin.domain.apistatus.LatencyPercentiles

data class TrafficSummary(
    val totalCalls: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointTraffic>,
    val estimatedUsers: EstimatedUsers,
    val latency: LatencyPercentiles,
    val hourlyCalls: List<HourlyBucket>,
    val hourlyErrors: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
)
