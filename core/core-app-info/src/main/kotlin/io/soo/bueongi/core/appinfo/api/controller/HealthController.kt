package io.soo.bueongi.core.appinfo.api.controller

import io.soo.bueongi.core.appinfo.api.response.HealthStatus
import io.soo.bueongi.core.appinfo.domain.service.HealthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val healthService: HealthService,
) {
    @GetMapping
    fun health(): HealthStatus {
        return healthService.getHealth()
    }
}
