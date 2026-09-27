package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component

@Component
class RouteFinder(private val tmapClient: TmapRouteClient) {
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

    // 안심시설에 들르는 길은 그만큼 더 걷게 된다. 얼마나 더 걷게 할지는 사용자의 안전 선호가 정한다.
    fun findSaferRoutes(query: RouteRequest, routes: List<TmapRoute>, waypointPlans: List<List<GeoPoint>>): List<TmapRoute> {
        val maxDistanceM: Double = maxDetourDistanceM(query, routes)

        // 안심시설 뭉치에 들렀다 가는 길을 계획마다 Tmap 에 묻고, 한도를 넘게 돌아가는 길은 버린다.
        val saferRoutes: ArrayList<TmapRoute> = ArrayList()
        for (waypoints in waypointPlans) {
            val saferRoute: TmapRoute = fetchRoute(query, VIA_SEARCH_OPTION, waypoints) ?: continue
            if (saferRoute.distanceM > maxDistanceM) {
                continue
            }
            saferRoutes.add(saferRoute)
        }
        return saferRoutes
    }

    // 허용 한도는 기본 경로 중 최단거리의 +20%(균형)·+35%(안전)·+60%(최대안전).
    private fun maxDetourDistanceM(query: RouteRequest, routes: List<TmapRoute>): Double {
        val detourBudgetRatio: Double = DETOUR_DISTANCE_BUDGETS.getValue(query.safetyPreference)
        val shortestDistanceM: Double = routes.minOf { it.distanceM }
        return shortestDistanceM * (1 + detourBudgetRatio)
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

private fun RouteRequest.endName(): String {
    return destinationName.trim().ifEmpty { "목적지" }
}

internal fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> {
    val points: ArrayList<GeoPoint> = ArrayList(size)
    for (coord in this) {
        points.add(GeoPoint(coord.lat, coord.lng))
    }
    return points
}

private fun GeoPoint.toTmapCoord(): TmapCoord {
    return TmapCoord(lat, lng)
}
