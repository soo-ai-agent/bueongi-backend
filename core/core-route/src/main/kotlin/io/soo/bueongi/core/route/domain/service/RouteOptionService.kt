package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.core.common.route.RoutePlannedHandler
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.api.response.RouteOption
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster
import io.soo.bueongi.core.route.domain.safety.service.SafetySpotWaypointPlanner
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import org.springframework.stereotype.Service

@Service
class RouteOptionService(
    private val routeFinder: RouteFinder,
    private val waypointPlanner: SafetySpotWaypointPlanner,
    private val candidateMerger: RouteCandidateMerger,
    private val typeAssigner: RouteTypeAssigner,
    private val assembler: RouteOptionAssembler,
    private val routePlannedHandler: RoutePlannedHandler,
) {
    fun find(route: RouteRequest): List<RouteOption> {
        val baseRouteList: List<RouteCandidate> = routeFinder.findRouteList(route)
        if (baseRouteList.isEmpty()) {
            throw RouteSearchUnavailableException()
        }
        val firstRoutePath: List<GeoPoint> = baseRouteList.first().path

        // 길을 잃어 다시 부른 것이면 빨리 답해야 해서 우회를 찾지 않는다.
        val routeList: ArrayList<RouteCandidate> = ArrayList(baseRouteList)
        if (!route.reroute) {
            val clusters: List<SafetySpotCluster> = waypointPlanner.clusterSafetySpots(firstRoutePath)
            val waypointPlans: List<List<GeoPoint>> = waypointPlanner.pickWaypoints(route.origin, route.destination, clusters)
            routeList.addAll(routeFinder.findSaferRouteList(route, baseRouteList, waypointPlans))
        }

        routePlannedHandler.onRoutePlanned(firstRoutePath)
        val candidates: List<RouteCandidate> = candidateMerger.merge(routeList)
        val scoredRoutes: List<ScoredRoute> = typeAssigner.assignOneRoutePerType(candidates)
        return assembler.assemble(scoredRoutes, route.origin, route.destination)
    }
}
