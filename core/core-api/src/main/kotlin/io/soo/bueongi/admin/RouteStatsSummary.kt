package io.soo.bueongi.admin

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

data class RouteStatsSummary(
    val total: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointCount>,
    val byPreference: List<PreferenceCount>,
    val topDestinations: List<DestinationCount>,
    val hourlyTrend: List<HourlyBucket>,
)
