package io.soo.bueongi.core.ops.domain.traffic.model

import io.soo.bueongi.support.util.HourlyBucket

data class AdminTraffic(
    val totalCalls: Int,
    val successCount: Int,
    val failureCount: Int,
    val callsToday: Int,
    val windowSize: Int,
    val windowCap: Int,
    val byEndpoint: List<EndpointTraffic>,
    val hourlyCalls: List<HourlyBucket>,
)
