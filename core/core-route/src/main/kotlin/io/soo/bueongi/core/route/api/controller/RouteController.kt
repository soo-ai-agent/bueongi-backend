package io.soo.bueongi.core.route.api.controller

import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.domain.model.RouteOption
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/routes")
class RouteController(
    private val safeRouteService: SafeRouteService,
) {
    @PostMapping
    fun findRouteOptions(@RequestBody request: RouteRequest): List<RouteOption> {
        return safeRouteService.findRouteOptions(request)
    }
}
