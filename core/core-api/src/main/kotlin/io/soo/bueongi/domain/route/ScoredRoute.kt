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
 * 채점·유형 배정이 끝난 후보(응답 변환 직전의 중간 표현).
 * [type] 은 [RouteType] — TYPE_NAME 키 집합(전 유형)과 일치한다(toRouteOptions 의 getValue 전제).
 */
internal data class ScoredRoute(val candidate: RouteCandidate, val score: SafetyScore, val type: RouteType)
