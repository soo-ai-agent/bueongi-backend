package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/** 목적지별 추천 요청 수 1건(상위 노출용). */
data class DestinationCount(
    val name: String,
    val count: Int,
)
