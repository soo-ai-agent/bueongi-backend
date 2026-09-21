package io.soo.bueongi.core.route.stats.api.controller

import io.soo.bueongi.core.route.stats.domain.service.RouteStatsService
import io.soo.bueongi.core.route.stats.domain.model.RouteStatsSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RouteStatsController(
    private val routeStatsService: RouteStatsService,
) {

    @GetMapping("/api/admin/route-stats")
    fun routeStats(): RouteStatsSummary =
        routeStatsService.summarize()
}
