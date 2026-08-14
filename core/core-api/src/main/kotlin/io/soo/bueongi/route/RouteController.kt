package io.soo.bueongi.route

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
