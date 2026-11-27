package io.soo.bueongi.core.safetyspot.domain.police.service

import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPolicePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NATIONWIDE
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.police.PoliceSpot
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
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
) {
    private val log: Logger = LoggerFactory.getLogger(PoliceFillService::class.java)

    private val inFlight: AtomicBoolean = AtomicBoolean(false)

    fun fill(): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (retryGate.isCoolingDown(NATIONWIDE)) {
            return 0
        }
        if (cache.isCached(SafetySpotType.POLICE, NATIONWIDE)) {
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
        val fetched: List<SafemapPolicePoint>? = client.fetch()
        if (fetched == null) {
            retryGate.record(NATIONWIDE)
            log.warn("{} 수집 실패 — 기존 데이터 유지, 쉬는 시간 뒤 다시 시도 (범위={})", SafetySpotType.POLICE.label, NATIONWIDE)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        // 좌표를 하나도 못 받았어도 기록해 재수집 주기 동안 다시 받지 않게 한다.
        liveStore.replaceRegion(SafetySpotType.POLICE, NATIONWIDE, points)
        if (points.isEmpty()) {
            log.warn("{} {}건을 받았으나 좌표가 없어 적재 0건 (범위={})", SafetySpotType.POLICE.label, fetched.size, NATIONWIDE)
            return 0
        }
        log.info("치안시설 채움: 받은={}, 좌표있음={}", fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<SafemapPolicePoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                PoliceSpot(
                    lat = row.lat,
                    lng = row.lng,
                    name = row.name,
                    standardSigunguCode = row.sigungu,
                    address = row.address,
                    phone = row.phone,
                ),
            )
        }
        return points
    }
}
