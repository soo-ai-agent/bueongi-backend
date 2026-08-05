package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service

/** 안심강도 선호별 요청 수 1건. [preference] 는 JSON 으로 requestValue(balanced|safe|safest)로 직렬화된다. */
data class PreferenceCount(
    val preference: SafetyPreference,
    val count: Int,
)
