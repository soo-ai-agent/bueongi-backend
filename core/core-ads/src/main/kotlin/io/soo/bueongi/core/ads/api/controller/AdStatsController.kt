package io.soo.bueongi.core.ads.api.controller

import io.soo.bueongi.core.ads.domain.service.AdStatsService
import io.soo.bueongi.core.ads.domain.model.AdStatsSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AdStatsController(
    private val adStatsService: AdStatsService,
) {

    @GetMapping("/api/admin/ad-stats")
    fun adStats(): AdStatsSummary =
        adStatsService.summarize()
}
