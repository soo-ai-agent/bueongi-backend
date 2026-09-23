package io.soo.bueongi.core.route.api.controller

import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.api.request.toValidatedQuery
import io.soo.bueongi.core.route.api.response.SafetySpotsResponse
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.model.RouteQuery
import io.soo.bueongi.core.route.domain.model.RouteSafetySpots
import io.soo.bueongi.core.route.domain.service.RouteSafetySpotService
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/routes")
class RouteController(
    private val routeService: RouteService,
    private val routeSafetySpotService: RouteSafetySpotService,
    private val safeRouteService: SafeRouteService,
    private val routeTypeCatalog: RouteTypeCatalog,
) {
    @PostMapping("/compare")
    fun compare(
        @Valid @RequestBody request: RouteRequest,
    ): List<RouteOption> {
        return routeService.compare(request.toValidatedQuery(routeTypeCatalog))
    }

    @PostMapping("/safe-compare")
    fun safeCompare(
        @Valid @RequestBody request: RouteRequest,
    ): List<RouteOption> {
        return safeRouteService.safeCompare(request.toValidatedQuery(routeTypeCatalog))
    }

    @PostMapping("/safety-spots")
    fun safetySpots(
        @Valid @RequestBody request: RouteRequest,
    ): SafetySpotsResponse {
        val query: RouteQuery = request.toValidatedQuery(routeTypeCatalog)
        val safetySpots: RouteSafetySpots = routeSafetySpotService.alongRoute(query)
        return SafetySpotsResponse.from(safetySpots)
    }
}
