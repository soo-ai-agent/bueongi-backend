package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/** 엔드포인트별 추천 요청 수 1건. */
data class EndpointCount(
    /** compare | safe-compare. */
    val endpoint: String,
    val count: Int,
)
