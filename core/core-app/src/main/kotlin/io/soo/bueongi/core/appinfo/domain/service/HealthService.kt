package io.soo.bueongi.core.appinfo.domain.service

import io.soo.bueongi.core.appinfo.api.response.HealthStatus
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class HealthService(
    private val shareService: ShareService,
    @Value("\${spring.application.name}")
    private val serviceName: String,
) {
    fun getHealth(): HealthStatus {
        val locationStore: String = shareService.storeBackendLabel()
        return HealthStatus.from(serviceName, locationStore)
    }
}
