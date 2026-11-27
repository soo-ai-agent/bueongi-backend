package io.soo.bueongi.core.safetyspot.domain.safehouse.service

import io.soo.bueongi.client.safe182.ChildSafeHouseClient
import io.soo.bueongi.client.safe182.ChildSafeHousePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.safehouse.model.MapTile
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.safehouse.SafeHouseSpot
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ChildSafeHouseFillService(
    private val client: ChildSafeHouseClient,
    private val cache: SafetySpotRegionCache,
    private val safetySpotWriter: SafetySpotWriter,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
) {
    private val log: Logger = LoggerFactory.getLogger(ChildSafeHouseFillService::class.java)

    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    fun fill(tile: MapTile): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (retryGate.isCoolingDown(tile.code)) {
            return 0
        }
        if (cache.isCached(SafetySpotType.SAFEHOUSE, tile.code)) {
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
        if (fetched == null) {
            retryGate.record(tile.code)
            log.warn("{} 수집 실패 — 기존 데이터 유지, 쉬는 시간 뒤 다시 시도 (범위={})", SafetySpotType.SAFEHOUSE.label, tile.code)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        // 바다·산처럼 시설이 없는 칸도 있다 — 빈 결과도 기록해 재수집 주기 동안 다시 받지 않게 한다.
        safetySpotWriter.replaceRegion(SafetySpotType.SAFEHOUSE, tile.code, points)
        if (points.isEmpty()) {
            return 0
        }
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
