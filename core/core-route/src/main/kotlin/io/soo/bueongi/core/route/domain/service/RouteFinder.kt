package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.safety.model.SafeAnchor
import io.soo.bueongi.core.route.domain.safety.service.SafeDetourPlanner
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component

@Component
class RouteFinder(
    private val tmapClient: TmapRouteClient,
    private val detourPlanner: SafeDetourPlanner,
    private val candidateMerger: RouteCandidateMerger,
) {
    val isEnabled: Boolean get() = tmapClient.isEnabled

    fun findFromTmap(query: RouteRequest): List<TmapRoute> {
        val routes: ArrayList<TmapRoute> = ArrayList()
        for (searchOption in SEARCH_OPTIONS) {
            val route: TmapRoute? =
                tmapClient.fetchRoute(
                    origin = query.origin.toTmapCoord(),
                    destination = query.destination.toTmapCoord(),
                    destinationName = query.endName(),
                    searchOption = searchOption,
                    passList = emptyList(),
                )
            if (route == null) {
                continue
            }
            routes.add(route)
        }
        return routes
    }

    fun findSaferRoutes(
        query: RouteRequest,
        tmapRoutes: List<TmapRoute>,
    ): List<RouteCandidate> {
        val detours: ArrayList<TmapRoute> = ArrayList()
        // 경로 이탈로 앱이 다시 부른 것은 우회를 찾지 않는다 — 길 잃은 사람에게는 빨리 답한다.
        if (!query.reroute) {
            val directPath: List<GeoPoint> = tmapRoutes.first().path.toGeoPoints()
            val anchors: List<SafeAnchor> = detourPlanner.extractSafeAnchors(directPath)
            val waypointCombos: List<List<GeoPoint>> =
                detourPlanner.pickDetourWaypoints(query.origin, query.destination, anchors)

            val budget: Double = DETOUR_BUDGETS.getValue(query.safetyPreference)
            val maxDistanceM: Double = tmapRoutes.minOf { it.distanceM } * (1 + budget)
            for (waypoints in waypointCombos) {
                val detour: TmapRoute? =
                    tmapClient.fetchRoute(
                        origin = query.origin.toTmapCoord(),
                        destination = query.destination.toTmapCoord(),
                        destinationName = query.endName(),
                        searchOption = VIA_SEARCH_OPTION,
                        passList = waypoints.map { it.toTmapCoord() },
                    )
                if (detour == null) {
                    continue
                }
                if (detour.distanceM <= maxDistanceM) {
                    detours.add(detour)
                }
            }
        }

        val addedKeys: HashSet<Long> = HashSet()
        val candidates: ArrayList<RouteCandidate> = ArrayList()
        for (route in tmapRoutes + detours) {
            if (!addedKeys.add(route.identityKey)) {
                continue
            }
            candidates.add(
                RouteCandidate(
                    searchOption = route.searchOption,
                    path = route.path.toGeoPoints(),
                    distanceM = route.distanceM,
                    timeS = route.timeS,
                    steps = route.steps,
                ),
            )
        }
        return candidateMerger.mergeNearDuplicates(candidates)
    }

    private companion object {
        // Tmap searchOption 규격: 추천=0, 대로우선=4, 최단=10.
        val SEARCH_OPTIONS: List<String> = listOf("0", "4", "10")

        const val VIA_SEARCH_OPTION: String = "0"

        val DETOUR_BUDGETS: Map<SafetyPreference, Double> =
            mapOf(
                SafetyPreference.BALANCED to 0.2,
                SafetyPreference.SAFE to 0.35,
                SafetyPreference.SAFEST to 0.6,
            )
    }
}

// Tmap 이 안내 문구에 쓰는 이름 — 비면 화면에 빈 칸이 나가므로 대신 넣는다.
private fun RouteRequest.endName(): String = destinationName.trim().ifEmpty { "목적지" }

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
