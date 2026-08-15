package io.soo.bueongi.core.domain.route

import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.core.domain.facility.SafeAnchor
import io.soo.bueongi.core.domain.facility.SafeDetourPlanner
import io.soo.bueongi.core.domain.facility.SafetyBreakdown
import io.soo.bueongi.core.domain.facility.SafetyScore
import io.soo.bueongi.core.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.roundToInt

data class RouteCandidate(
    val searchOption: String,
    val path: List<GeoPoint>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
)
