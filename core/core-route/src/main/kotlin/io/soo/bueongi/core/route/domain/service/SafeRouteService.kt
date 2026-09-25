package io.soo.bueongi.core.route.domain.service

import io.soo.bueongi.client.tmap.TmapRoute
import io.soo.bueongi.core.common.route.RoutePlannedHandler
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.error.RouteSearchUnavailableException
import io.soo.bueongi.core.route.domain.model.RouteCandidate
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.ScoredRoute
import org.springframework.stereotype.Service

@Service
class SafeRouteService(
    private val routeFinder: RouteFinder,
    private val candidateMerger: RouteCandidateMerger,
    private val typeAssigner: RouteTypeAssigner,
    private val assembler: RouteOptionAssembler,
    private val routePlannedHandler: RoutePlannedHandler,
) {
    fun safeCompare(query: RouteRequest): List<RouteOption> {
        if (!routeFinder.isEnabled) {
            throw RouteSearchUnavailableException()
        }
        val routes: ArrayList<TmapRoute> = ArrayList(routeFinder.findRoutes(query))
        if (routes.isEmpty()) {
            throw RouteSearchUnavailableException()
        }
        // 길을 잃어 다시 부른 것이면 빨리 답해야 해서 우회를 찾지 않는다.
        if (!query.reroute) {
            routes.addAll(routeFinder.findSaferRoutes(query, routes))
        }

        // 경로가 지나는 지역의 안심시설을 받아 두게 한다. 다음 요청부터 그 지역 안전 점수가 정확해진다.
        routePlannedHandler.onRoutePlanned(routes.first().path.toGeoPoints())

        val candidates: List<RouteCandidate> = candidateMerger.toCandidates(routes)
        val assigned: List<ScoredRoute> = typeAssigner.assignOneRoutePerType(candidates)
        return assembler.assemble(assigned, query.origin, query.destination)
    }
}
