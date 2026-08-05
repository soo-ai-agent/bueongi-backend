package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/**
 * route-stats 응답 전체. [total] 은 창 내 총 추천 요청 수, [windowSize] 는 집계에 쓴 기록 수(둘 다 창 상한 이하).
 */
data class RouteStatsSummary(
    val total: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointCount>,
    val byPreference: List<PreferenceCount>,
    val topDestinations: List<DestinationCount>,
    val hourlyTrend: List<HourlyBucket>,
)
