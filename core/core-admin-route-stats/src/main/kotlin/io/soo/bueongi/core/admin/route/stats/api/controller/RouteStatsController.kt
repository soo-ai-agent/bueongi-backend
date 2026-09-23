package io.soo.bueongi.core.admin.route.stats.api.controller

import io.soo.bueongi.core.admin.route.stats.api.response.RouteStatsResponse
import io.soo.bueongi.core.admin.route.stats.domain.service.RouteStatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RouteStatsController(
    private val routeStatsService: RouteStatsService,
) {
    @GetMapping("/api/admin/route-stats")
    fun routeStats(): RouteStatsResponse {
        return routeStatsService.summarize()
    }
}
