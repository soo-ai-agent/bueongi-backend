package io.soo.bueongi.core.safetyspot.domain.safehouse.service

import io.soo.bueongi.client.safe182.ChildSafeHouseClient
import io.soo.bueongi.client.safe182.ChildSafeHousePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.core.safetyspot.domain.safehouse.model.MapTile
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.safehouse.SafeHouseSpot
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ChildSafeHouseFillService(
    private val client: ChildSafeHouseClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
    private val events: SafetySpotFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(ChildSafeHouseFillService::class.java)

    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    fun isFilled(tile: MapTile): Boolean {
        return cache.isCached(SafetySpotType.SAFEHOUSE, tile.code)
    }

    fun fill(tile: MapTile): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (retryGate.isCoolingDown(tile.code)) {
            return 0
        }
        if (isFilled(tile)) {
            return 0
        }
        if (inFlight.putIfAbsent(tile.code, true) != null) {
            return 0
        }
        return try {
            fetchAndStore(tile)
        } finally {
            inFlight.remove(tile.code)
        }
    }

    private fun fetchAndStore(tile: MapTile): Int {
        val fetched: List<ChildSafeHousePoint>? = client.fetchWithin(tile.toBounds())
        events.fetchPerformed(SafetySpotType.SAFEHOUSE, tile.code, fetched?.size)
        if (fetched == null) {
            retryGate.recordFailure(tile.code)
            events.fetchFailed(SafetySpotType.SAFEHOUSE, tile.code)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        // 바다·산처럼 시설이 없는 칸도 있다 — 빈 결과도 캐시로 남겨 매번 다시 받지 않게 한다.
        cache.save(SafetySpotType.SAFEHOUSE, tile.code, points)
        if (points.isEmpty()) {
            return 0
        }
        liveStore.replaceRegion(SafetySpotType.SAFEHOUSE, tile.code, points)
        log.info("아동안전지킴이집 격자 채움: tile={}, 받은={}", tile.code, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<ChildSafeHousePoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafeHouseSpot(
                    lat = row.lat,
                    lng = row.lng,
                    name = row.name,
                    address = row.address,
                    phone = row.phone,
                ),
            )
        }
        return points
    }
}
