package io.soo.bueongi.core.ops.api.controller

import io.soo.bueongi.core.ops.domain.traffic.service.RequestMetricsService
import io.soo.bueongi.core.ops.domain.traffic.model.TrafficSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TrafficController(
    private val requestMetricsService: RequestMetricsService,
) {

    @GetMapping("/api/admin/traffic")
    fun traffic(): TrafficSummary =
        requestMetricsService.summarize()
}
