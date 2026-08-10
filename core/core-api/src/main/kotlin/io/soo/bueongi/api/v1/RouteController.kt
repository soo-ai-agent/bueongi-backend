package io.soo.bueongi.api.v1

import io.soo.bueongi.api.v1.request.RouteRequest
import io.soo.bueongi.api.v1.request.toValidatedQuery
import io.soo.bueongi.domain.route.FacilitiesResponse
import io.soo.bueongi.domain.route.RouteFacilityService
import io.soo.bueongi.domain.route.RouteOption
import io.soo.bueongi.domain.route.RouteService
import io.soo.bueongi.domain.route.SafeRouteService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/routes")
class RouteController(
    private val routeService: RouteService,
    private val routeFacilityService: RouteFacilityService,
    private val safeRouteService: SafeRouteService,
) {

    @PostMapping("/compare")
    fun compare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        routeService.compare(request.toValidatedQuery())

    @PostMapping("/safe-compare")
    fun safeCompare(@Valid @RequestBody request: RouteRequest): List<RouteOption> =
        safeRouteService.safeCompare(request.toValidatedQuery())

    @PostMapping("/facilities")
    fun facilities(@Valid @RequestBody request: RouteRequest): FacilitiesResponse =
        routeFacilityService.alongRoute(request.toValidatedQuery())
}
