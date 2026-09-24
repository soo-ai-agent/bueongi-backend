package io.soo.bueongi.core.safetyspot.domain.streetlamp.service

import io.soo.bueongi.client.safetydata.streetlamp.StreetLampClient
import io.soo.bueongi.client.safetydata.streetlamp.StreetLampPoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NationwideFillService
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.StreetLampSpot
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import org.springframework.stereotype.Service

@Service
class StreetLampFillService(
    private val client: StreetLampClient,
    cache: SafetySpotRegionCache,
    liveStore: SafetySpotLiveStore,
    retryGate: FillFailureCooldown = FillFailureCooldown(),
    events: SafetySpotFillEventPublisher,
) : NationwideFillService<StreetLampPoint>(cache, liveStore, retryGate, events) {
    override val type: SafetySpotType = SafetySpotType.STREET_LAMP

    override val isSourceEnabled: Boolean
        get() = client.isEnabled

    override fun fetch(): List<StreetLampPoint>? = client.fetchStreetLamps()

    override fun toSafetySpots(fetched: List<StreetLampPoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                StreetLampSpot(
                    lat = row.lat,
                    lng = row.lng,
                    sigungu = row.sigungu,
                    address = row.address,
                ),
            )
        }
        return points
    }
}
