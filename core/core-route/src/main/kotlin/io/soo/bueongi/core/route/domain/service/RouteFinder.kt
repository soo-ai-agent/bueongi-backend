package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component

@Component
class RouteFinder(
    private val tmapClient: TmapRouteClient,
) {
    fun findRoutes(query: RouteRequest): List<TmapRoute> {
        val routes: ArrayList<TmapRoute> = ArrayList()
        for (searchOption in SEARCH_OPTIONS) {
            val route: TmapRoute? = fetchRoute(query, searchOption)
            if (route != null) {
                routes.add(route)
            }
        }
        return routes
    }

    fun findSaferRoutes(query: RouteRequest, routes: List<TmapRoute>, waypointCombos: List<List<GeoPoint>>): List<TmapRoute> {
        // 우회는 안전을 사려고 거리를 내는 거래다 — 안전 선호가 높을수록 더 돌아가도 된다.
        // 허용 한도는 기본 경로 중 최단거리의 +20%(균형)·+35%(안전)·+60%(최대안전).
        val detourBudgetRatio: Double = DETOUR_DISTANCE_BUDGETS.getValue(query.safetyPreference)
        val shortestDistanceM: Double = routes.minOf { it.distanceM }
        val maxDistanceM: Double = shortestDistanceM * (1 + detourBudgetRatio)

        // 안심시설 뭉치에 들렀다 가는 길을 조합마다 Tmap 에 묻고, 한도를 넘게 돌아가는 길은 버린다.
        val saferRoutes: ArrayList<TmapRoute> = ArrayList()
        for (waypoints in waypointCombos) {
            val saferRoute: TmapRoute? = fetchRoute(query, VIA_SEARCH_OPTION, waypoints)
            if (saferRoute == null) {
                continue
            }
            if (saferRoute.distanceM > maxDistanceM) {
                continue
            }
            saferRoutes.add(saferRoute)
        }
        return saferRoutes
    }

    private fun fetchRoute(query: RouteRequest, searchOption: String, waypoints: List<GeoPoint> = emptyList()): TmapRoute? {
        return tmapClient.fetchRoute(
            origin = query.origin.toTmapCoord(),
            destination = query.destination.toTmapCoord(),
            destinationName = query.endName(),
            searchOption = searchOption,
            passList = waypoints.map { it.toTmapCoord() },
        )
    }

    private companion object {
        // Tmap searchOption 규격: 추천=0, 대로우선=4, 최단=10.
        val SEARCH_OPTIONS: List<String> = listOf("0", "4", "10")
        const val VIA_SEARCH_OPTION: String = "0"
        val DETOUR_DISTANCE_BUDGETS: Map<SafetyPreference, Double> =
            mapOf(
                SafetyPreference.BALANCED to 0.2,
                SafetyPreference.SAFE to 0.35,
                SafetyPreference.SAFEST to 0.6,
            )
    }
}

private fun RouteRequest.endName(): String = destinationName.trim().ifEmpty { "목적지" }

internal fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
