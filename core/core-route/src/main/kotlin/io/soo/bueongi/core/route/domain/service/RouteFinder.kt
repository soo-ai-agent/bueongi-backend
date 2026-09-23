package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster
import io.soo.bueongi.core.route.domain.safety.service.SafeWaypointFinder
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component

@Component
class RouteFinder(
    private val tmapClient: TmapRouteClient,
    private val waypointFinder: SafeWaypointFinder,
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

    fun findSaferRoutes(query: RouteRequest, tmapRoutes: List<TmapRoute>): List<TmapRoute> {
        val firstRoutePath: List<GeoPoint> = tmapRoutes.first().path.toGeoPoints()
        val safetySpotClusters: List<SafetySpotCluster> = waypointFinder.findSafetySpotClusters(firstRoutePath)
        val waypointCombos: List<List<GeoPoint>> = waypointFinder.pickWaypoints(query.origin, query.destination, safetySpotClusters)

        val budget: Double = DETOUR_DISTANCE_BUDGETS.getValue(query.safetyPreference)
        val maxDistanceM: Double = tmapRoutes.minOf { it.distanceM } * (1 + budget)
        val saferRoutes: ArrayList<TmapRoute> = ArrayList()
        for (waypoints in waypointCombos) {
            val saferRoute: TmapRoute? =
                tmapClient.fetchRoute(
                    origin = query.origin.toTmapCoord(),
                    destination = query.destination.toTmapCoord(),
                    destinationName = query.endName(),
                    searchOption = VIA_SEARCH_OPTION,
                    passList = waypoints.map { it.toTmapCoord() },
                )
            if (saferRoute == null) {
                continue
            }
            if (saferRoute.distanceM <= maxDistanceM) {
                saferRoutes.add(saferRoute)
            }
        }
        return saferRoutes
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
private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }
private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
