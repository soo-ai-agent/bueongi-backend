package io.soo.bueongi.core.ops.api

import io.soo.bueongi.core.ops.domain.traffic.service.RequestMetricsService
import io.soo.bueongi.core.ops.domain.traffic.model.TrafficSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 수신 요청 지표. 경로가 /api/admin 아래인 것은 프론트 계약이다. */
@RestController
class TrafficController(
    private val requestMetricsService: RequestMetricsService,
) {

    @GetMapping("/api/admin/traffic")
    fun traffic(): TrafficSummary = requestMetricsService.summarize()
}
