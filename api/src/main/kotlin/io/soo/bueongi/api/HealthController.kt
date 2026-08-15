package io.soo.bueongi.api

import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val remoteProvider: RemotePlaceProvider,
    private val shareService: ShareService,
) {

    @GetMapping
    fun health(): HealthStatus {
        val placeSource: String = if (remoteProvider.isEnabled) {
            PLACE_SOURCE_LIVE
        } else {
            PLACE_SOURCE_SEED
        }
        return HealthStatus(
            status = STATUS_UP,
            service = SERVICE_NAME,
            placeSource = placeSource,
            locationStore = shareService.storeBackendLabel(),
        )
    }

    private companion object {
        const val STATUS_UP: String = "UP"
        const val SERVICE_NAME: String = "bueongi-backend"
        const val PLACE_SOURCE_LIVE: String = "live"
        const val PLACE_SOURCE_SEED: String = "seed"
    }
}
