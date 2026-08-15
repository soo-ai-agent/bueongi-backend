package io.soo.bueongi.core.admin.domain.routestats

import io.soo.bueongi.core.admin.domain.traffic.EndpointCount
import io.soo.bueongi.support.util.HourlyBucket

data class RouteStatsSummary(
    val total: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointCount>,
    val byPreference: List<PreferenceCount>,
    val topDestinations: List<DestinationCount>,
    val hourlyTrend: List<HourlyBucket>,
)
