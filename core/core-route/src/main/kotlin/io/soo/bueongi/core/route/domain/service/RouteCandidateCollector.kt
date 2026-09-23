package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapCoord
import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.api.request.RouteRequest
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

    fun collectBase(query: RouteRequest): List<TmapRoute> =
        tmapClient.fetchRoutes(
            query.origin.toTmapCoord(),
            query.destination.toTmapCoord(),
            query.destinationName,
        )

    fun collectAll(
        query: RouteRequest,
        base: List<TmapRoute>,
    ): List<RouteCandidate> {
        val detours: List<TmapRoute> = collectSafeDetours(query, base)

        return candidateMerger.mergeNearDuplicates(
            dedupeByIdentity(base + detours).map { it.toCandidate() },
        )
    }

    private fun collectSafeDetours(
        query: RouteRequest,
        base: List<TmapRoute>,
    ): List<TmapRoute> {
        if (query.reroute) {
            return emptyList()
        }
        val directPath: List<GeoPoint> = base.first().path.toGeoPoints()
        val anchors: List<SafeAnchor> = detourPlanner.extractSafeAnchors(directPath)
        val waypointCombos: List<List<GeoPoint>> =
            detourPlanner.pickDetourWaypoints(query.origin, query.destination, anchors)

        val budget: Double = DETOUR_BUDGETS.getValue(query.safetyPreference)
        val maxDistanceM: Double = base.minOf { it.distanceM } * (1 + budget)
        val detours: ArrayList<TmapRoute> = ArrayList()
        for (waypoints in waypointCombos) {
            val route: TmapRoute =
                tmapClient.fetchRouteVia(
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
        val seen: HashSet<Long> = HashSet()
        return routes.filter { seen.add(it.identityKey) }
    }

    private companion object {
        val DETOUR_BUDGETS: Map<SafetyPreference, Double> =
            mapOf(
                SafetyPreference.BALANCED to 0.2,
                SafetyPreference.SAFE to 0.35,
                SafetyPreference.SAFEST to 0.6,
            )
    }
}

private fun TmapRoute.toCandidate(): RouteCandidate =
    RouteCandidate(searchOption = searchOption, path = path.toGeoPoints(), distanceM = distanceM, timeS = timeS, steps = steps)

private fun List<TmapCoord>.toGeoPoints(): List<GeoPoint> = map { GeoPoint(it.lat, it.lng) }

private fun GeoPoint.toTmapCoord(): TmapCoord = TmapCoord(lat, lng)
