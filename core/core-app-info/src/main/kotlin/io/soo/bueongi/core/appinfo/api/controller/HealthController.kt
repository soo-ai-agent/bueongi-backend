package io.soo.bueongi.core.appinfo.api.controller

import io.soo.bueongi.core.appinfo.api.response.HealthStatus
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val shareService: ShareService,
    @Value("\${spring.application.name}")
    private val serviceName: String,
) {
    @GetMapping
    fun health(): HealthStatus {
        return HealthStatus(
            status = STATUS_UP,
            service = serviceName,
            locationStore = shareService.storeBackendLabel(),
        )
    }

    private companion object {
        const val STATUS_UP: String = "UP"
    }
}
