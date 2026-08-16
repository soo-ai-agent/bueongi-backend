package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.route.domain.safety.model.SafeAnchor
import io.soo.bueongi.core.route.domain.safety.service.SafeDetourPlanner
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component

@Component
class RouteCandidateCollector(
    private val tmapClient: TmapRouteClient,
    private val detourPlanner: SafeDetourPlanner,
    private val candidateMerger: RouteCandidateMerger,
) {

    val isEnabled: Boolean get() = tmapClient.isEnabled

    /** 전멸하면 빈 목록 — 폴백 여부는 호출부가 정한다. */
    fun collectBase(query: RouteQuery): List<TmapRoute> =
        tmapClient.fetchRoutes(
            query.origin.toTmapCoord(),
            query.destination.toTmapCoord(),
            query.destinationName,
        )

    fun collectAll(query: RouteQuery, base: List<TmapRoute>): List<RouteCandidate> {
        val detours: List<TmapRoute> = collectSafeDetours(query, base)

        // Tmap 은 옵션만 다르면 사실상 같은 길을 준다 — 완전 일치와 근사 겹침을 따로 걸러야 한다.
        return candidateMerger.mergeNearDuplicates(
            dedupeByIdentity(base + detours).map { it.toCandidate() },
        )
    }

    /** 예산을 넘는 후보는 버린다 — 너무 멀리 도는 길은 안전해도 쓰지 않는다. */
    private fun collectSafeDetours(query: RouteQuery, base: List<TmapRoute>): List<TmapRoute> {
        val directPath: List<GeoPoint> = base.first().path.toGeoPoints()
        val anchors: List<SafeAnchor> = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos: List<List<GeoPoint>> =
            detourPlanner.pickDetourWaypoints(query.origin, query.destination, anchors)

        val budget: Double = DETOUR_BUDGETS.getValue(query.safetyPreference)
        val maxDistanceM: Double = base.minOf { it.distanceM } * (1 + budget)
        val detours: ArrayList<TmapRoute> = ArrayList<TmapRoute>()
        // 조합 하나가 Tmap 호출 한 번이라, 받아 본 뒤에야 예산 초과를 안다.
        for (waypoints in waypointCombos) {
            val route: TmapRoute = tmapClient.fetchRouteVia(
                query.origin.toTmapCoord(),
                query.destination.toTmapCoord(),
                waypoints.map { it.toTmapCoord() },
                destName = query.destinationName,
            ) ?: continue
            if (route.distanceM <= maxDistanceM) {
                detours.add(route)
            }
        }
        return detours
    }

    private fun dedupeByIdentity(routes: List<TmapRoute>): List<TmapRoute> {
        val seen: HashSet<Long> = HashSet<Long>()
        return routes.filter { seen.add(it.identityKey) }
    }

    private companion object {

        val DETOUR_BUDGETS: Map<SafetyPreference, Double> = mapOf(
            SafetyPreference.BALANCED to 0.2,
            SafetyPreference.SAFE to 0.35,
            SafetyPreference.SAFEST to 0.6,
        )
    }
}

private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> =
    map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord =
    TmapCoord(lat, lng)
