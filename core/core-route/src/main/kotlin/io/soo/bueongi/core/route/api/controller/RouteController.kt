package io.soo.bueongi.core.route.api.controller

import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.api.request.toValidatedQuery

import io.soo.bueongi.core.route.api.response.FacilitiesResponse
import io.soo.bueongi.core.route.domain.service.RouteFacilityService
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.SafeRouteService

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.soo.bueongi.core.route.domain.model.RouteFacilities
import io.soo.bueongi.core.route.domain.model.RouteQuery

@RestController
@RequestMapping("/api/routes")
class RouteController(
    private val routeService: RouteService,
    private val routeFacilityService: RouteFacilityService,
    private val safeRouteService: SafeRouteService,
    private val routeTypeCatalog: RouteTypeCatalog,
) {

    @PostMapping("/compare")
    fun compare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        routeService.compare(request.toValidatedQuery(routeTypeCatalog))

    @PostMapping("/safe-compare")
    fun safeCompare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        safeRouteService.safeCompare(request.toValidatedQuery(routeTypeCatalog))

    @PostMapping("/facilities")
    fun facilities(@Valid @RequestBody request: RouteRequest): FacilitiesResponse {
        val query: RouteQuery = request.toValidatedQuery(routeTypeCatalog)
        val facilities: RouteFacilities = routeFacilityService.alongRoute(query)
        return FacilitiesResponse.from(facilities)
    }
}
