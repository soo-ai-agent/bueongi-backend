package io.soo.bueongi.core.ops.api.controller

import io.soo.bueongi.core.ops.domain.external.model.ExternalApiSummary
import io.soo.bueongi.core.ops.domain.external.service.ExternalApiMetricsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ExternalApiController(
    private val externalApiMetricsService: ExternalApiMetricsService,
) {
    @GetMapping("/api/admin/external-calls")
    fun externalCalls(): ExternalApiSummary = externalApiMetricsService.summarize()
}
