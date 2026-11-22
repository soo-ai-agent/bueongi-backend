package io.soo.bueongi.core.safetyspot.domain.streetlamp.service

import io.soo.bueongi.client.safetydata.streetlamp.StreetLampClient
import io.soo.bueongi.client.safetydata.streetlamp.StreetLampPoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NATIONWIDE
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.safetyspot.streetlamp.StreetLampSpot
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

    fun fill(): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (retryGate.isCoolingDown(NATIONWIDE)) {
            return 0
        }
        if (cache.isCached(SafetySpotType.STREET_LAMP, NATIONWIDE)) {
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
        events.fetchPerformed(SafetySpotType.STREET_LAMP, NATIONWIDE, fetched?.size)
        if (fetched == null) {
            retryGate.record(NATIONWIDE)
            events.fetchFailed(SafetySpotType.STREET_LAMP, NATIONWIDE)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        // 좌표를 하나도 못 받았어도 캐시로 남겨 매번 다시 받지 않게 한다.
        cache.save(SafetySpotType.STREET_LAMP, NATIONWIDE, points)
        if (points.isEmpty()) {
            events.hadNoCoordinates(SafetySpotType.STREET_LAMP, NATIONWIDE, fetched.size)
            return 0
        }
        liveStore.replaceRegion(SafetySpotType.STREET_LAMP, NATIONWIDE, points)
        log.info("가로등 채움: 받은={}, 좌표있음={}", fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<StreetLampPoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                StreetLampSpot(
                    lat = row.lat,
                    lng = row.lng,
                    standardSigunguCode = row.sigungu,
                    address = row.address,
                ),
            )
        }
        return points
    }
}
