package io.soo.bueongi.core.safetyspot.domain.streetlamp.service

import io.soo.bueongi.client.safetydata.streetlamp.StreetLampClient
import io.soo.bueongi.client.safetydata.streetlamp.StreetLampPoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NATIONWIDE
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class StreetLampFillService(
    private val client: StreetLampClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
    private val events: SafetySpotFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(StreetLampFillService::class.java)

    private val inFlight: AtomicBoolean = AtomicBoolean(false)

    fun isFilled(): Boolean = cache.isCached(SafetySpotType.STREET_LAMP, NATIONWIDE)

    fun fill(): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (isFilled()) {
            return 0
        }
        if (retryGate.isCoolingDown(NATIONWIDE)) {
            return 0
        }
        if (!inFlight.compareAndSet(false, true)) {
            return 0
        }
        return try {
            fetchAndStore()
        } finally {
            inFlight.set(false)
        }
    }

    private fun fetchAndStore(): Int {
        val fetched: List<StreetLampPoint>? = client.fetchStreetLamps()
        if (fetched == null) {
            retryGate.recordFailure(NATIONWIDE)
            events.fetchFailed(SafetySpotType.STREET_LAMP, NATIONWIDE)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            cache.save(SafetySpotType.STREET_LAMP, NATIONWIDE, points)
            events.hadNoCoordinates(SafetySpotType.STREET_LAMP, NATIONWIDE, fetched.size)
            return 0
        }
        cache.save(SafetySpotType.STREET_LAMP, NATIONWIDE, points)
        liveStore.append(SafetySpotType.STREET_LAMP, points)
        log.info("가로등 채움: 받은={}, 좌표있음={}", fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<StreetLampPoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafetySpotPoint(
                    type = SafetySpotType.STREET_LAMP,
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
