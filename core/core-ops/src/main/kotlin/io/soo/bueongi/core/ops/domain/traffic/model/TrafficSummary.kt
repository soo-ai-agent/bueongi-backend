package io.soo.bueongi.core.ops.domain.traffic.model

import io.soo.bueongi.support.util.HourlyBucket

// totalCalls 이하 모든 칸은 **사용자 호출만** 센다. 관리자 호출은 adminCalls 에 따로 있다.
data class TrafficSummary(
    val totalCalls: Int,
    val successCount: Int,
    val failureCount: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointTraffic>,
    val estimatedUsers: EstimatedUsers,
    val latency: LatencyPercentiles,
    val hourlyCalls: List<HourlyBucket>,
    val hourlyErrors: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
    val adminCalls: AdminTraffic,
)
