package io.soo.bueongi.core.safetyspot.domain.police.service

import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPolicePoint
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
class PoliceFillService(
    private val client: SafemapPoliceClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
    private val events: SafetySpotFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(PoliceFillService::class.java)

    private val inFlight: AtomicBoolean = AtomicBoolean(false)

    fun isFilled(): Boolean = cache.isCached(SafetySpotType.POLICE, NATIONWIDE)

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
        val fetched: List<SafemapPolicePoint>? = client.fetchPoliceFacilities()
        if (fetched == null) {
            retryGate.recordFailure(NATIONWIDE)
            events.fetchFailed(SafetySpotType.POLICE, NATIONWIDE)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            cache.save(SafetySpotType.POLICE, NATIONWIDE, points)
            events.hadNoCoordinates(SafetySpotType.POLICE, NATIONWIDE, fetched.size)
            return 0
        }
        cache.save(SafetySpotType.POLICE, NATIONWIDE, points)
        liveStore.append(SafetySpotType.POLICE, points)
        log.info("치안시설 채움: 받은={}", points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<SafemapPolicePoint>): List<SafetySpotPoint> {
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
