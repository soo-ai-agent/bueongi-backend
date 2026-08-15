package io.soo.bueongi.core.ops.domain.traffic.model

import io.soo.bueongi.support.util.HourlyBucket

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
