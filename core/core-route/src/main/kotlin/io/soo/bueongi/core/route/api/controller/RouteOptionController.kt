package io.soo.bueongi.core.route.api.controller

import io.soo.bueongi.core.route.api.request.RouteRequest
import io.soo.bueongi.core.route.api.response.RouteOption
import io.soo.bueongi.core.route.domain.service.RouteOptionService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/routes")
class RouteOptionController(
    private val routeOptionService: RouteOptionService,
) {
    @PostMapping
    fun find(@RequestBody request: RouteRequest): List<RouteOption> {
        return routeOptionService.find(request)
    }
}
