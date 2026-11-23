package io.soo.bueongi.core.appinfo.domain.service

import io.soo.bueongi.core.appinfo.api.response.HealthStatus
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class HealthService(
    private val store: ShareSessionStore,
    @Value("\${spring.application.name}")
    private val serviceName: String,
) {
    fun get(): HealthStatus {
        val locationStore: String = store.backend
        return HealthStatus.from(serviceName, locationStore)
    }
}
