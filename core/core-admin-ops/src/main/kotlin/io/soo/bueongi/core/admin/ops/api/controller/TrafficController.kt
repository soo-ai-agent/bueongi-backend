package io.soo.bueongi.core.admin.ops.api.controller

import io.soo.bueongi.core.admin.ops.domain.traffic.model.TrafficSummary
import io.soo.bueongi.core.admin.ops.domain.traffic.service.RequestMetricsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TrafficController(private val requestMetricsService: RequestMetricsService) {
    @GetMapping("/api/admin/traffic")
    fun traffic(): TrafficSummary {
        return requestMetricsService.summarize()
    }
}
