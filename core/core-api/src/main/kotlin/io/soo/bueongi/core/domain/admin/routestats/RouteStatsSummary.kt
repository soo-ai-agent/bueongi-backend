package io.soo.bueongi.core.domain.admin.routestats

import io.soo.bueongi.core.domain.admin.traffic.EndpointCount
import io.soo.bueongi.support.util.HourlyBucket

data class RouteStatsSummary(
    val total: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointCount>,
    val byPreference: List<PreferenceCount>,
    val topDestinations: List<DestinationCount>,
    val hourlyTrend: List<HourlyBucket>,
)
