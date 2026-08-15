package io.soo.bueongi.core.route.api

import io.soo.bueongi.core.route.domain.stats.RouteStatsService
import io.soo.bueongi.core.route.domain.stats.RouteStatsSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 경로가 /api/admin 아래인 것은 프론트 계약이다 — 경로 추천 통계라 경로 모듈이 답한다. */
@RestController
class RouteStatsController(
    private val routeStatsService: RouteStatsService,
) {

    @GetMapping("/api/admin/route-stats")
    fun routeStats(): RouteStatsSummary = routeStatsService.summarize()
}
