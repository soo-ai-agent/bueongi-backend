package io.soo.bueongi.domain.route

import io.soo.bueongi.client.tmap.NavStep
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.domain.facility.SafeAnchor
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyBreakdown
import io.soo.bueongi.domain.facility.SafetyScore
import io.soo.bueongi.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.apilog.measure
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 채점 대상 도메인 경로 후보 — 클라이언트 [TmapRoute] 의 도메인 표현(좌표를 [GeoPoint] 로 매핑).
 * steps(단계 안내)는 값 그대로 응답에 실리므로 클라이언트 타입([NavStep])을 재사용한다.
 */
data class RouteCandidate(
    val searchOption: String,
    val path: List<GeoPoint>,
    val distanceM: Double,
    val timeS: Int,
    val steps: List<NavStep>,
)
