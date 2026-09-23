package io.soo.bueongi.core.safetyspot.domain.police.service

import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPolicePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NationwideFillService
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import org.springframework.stereotype.Service

@Service
class PoliceFillService(
    private val client: SafemapPoliceClient,
    cache: SafetySpotRegionCache,
    liveStore: SafetySpotLiveStore,
    retryGate: FillFailureCooldown = FillFailureCooldown(),
    events: SafetySpotFillEventPublisher,
) : NationwideFillService<SafemapPolicePoint>(cache, liveStore, retryGate, events) {
    override val type: SafetySpotType = SafetySpotType.POLICE

    override val isSourceEnabled: Boolean
        get() = client.isEnabled

    override fun fetch(): List<SafemapPolicePoint>? = client.fetchPoliceFacilities()

    override fun toSafetySpots(fetched: List<SafemapPolicePoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafetySpotPoint(
                    type = SafetySpotType.POLICE,
                    lat = row.lat,
                    lng = row.lng,
                    name = row.name,
                    sigungu = row.sigungu,
                    address = row.address,
                    phone = row.phone,
                ),
            )
        }
        return points
    }
}
