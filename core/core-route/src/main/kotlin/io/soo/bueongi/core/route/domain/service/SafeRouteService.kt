package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.route.RoutePlannedHandler
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster
import io.soo.bueongi.core.route.domain.safety.service.SafetySpotWaypointPlanner
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Service

@Service
class SafeRouteService(
    private val routeFinder: RouteFinder,
    private val waypointPlanner: SafetySpotWaypointPlanner,
    private val candidateMerger: RouteCandidateMerger,
    private val typeAssigner: RouteTypeAssigner,
    private val assembler: RouteOptionAssembler,
    private val routePlannedHandler: RoutePlannedHandler,
) {
    fun findRouteOptions(query: RouteRequest): List<RouteOption> {
        val baseRoutes: List<RouteCandidate> = routeFinder.findRoutes(query)
        if (baseRoutes.isEmpty()) {
            throw RouteSearchUnavailableException()
        }
        val firstRoutePath: List<GeoPoint> = baseRoutes.first().path

        // 길을 잃어 다시 부른 것이면 빨리 답해야 해서 우회를 찾지 않는다.
        val routes: ArrayList<RouteCandidate> = ArrayList(baseRoutes)
        if (!query.reroute) {
            val clusters: List<SafetySpotCluster> = waypointPlanner.clusterSafetySpots(firstRoutePath)
            val waypointPlans: List<List<GeoPoint>> = waypointPlanner.pickWaypoints(query.origin, query.destination, clusters)
            routes.addAll(routeFinder.findSaferRoutes(query, baseRoutes, waypointPlans))
        }

        routePlannedHandler.onRoutePlanned(firstRoutePath)
        val candidates: List<RouteCandidate> = candidateMerger.merge(routes)
        val scoredRoutes: List<ScoredRoute> = typeAssigner.assignOneRoutePerType(candidates)
        return assembler.assemble(scoredRoutes, query.origin, query.destination)
    }
}
