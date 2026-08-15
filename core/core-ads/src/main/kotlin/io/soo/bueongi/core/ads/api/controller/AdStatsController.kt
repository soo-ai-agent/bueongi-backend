package io.soo.bueongi.core.ads.api.controller

import io.soo.bueongi.core.ads.domain.service.AdStatsService
import io.soo.bueongi.core.ads.domain.model.AdStatsSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 경로가 /api/admin 아래인 것은 프론트 계약이다 — 광고 노출 통계라 광고 모듈이 답한다. */
@RestController
class AdStatsController(
    private val adStatsService: AdStatsService,
) {

    @GetMapping("/api/admin/ad-stats")
    fun adStats(): AdStatsSummary =
        adStatsService.summarize()
}
