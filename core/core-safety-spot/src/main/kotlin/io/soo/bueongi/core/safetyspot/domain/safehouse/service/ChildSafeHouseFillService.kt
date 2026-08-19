package io.soo.bueongi.core.safetyspot.domain.safehouse.service

import io.soo.bueongi.client.safe182.ChildSafeHouseClient
import io.soo.bueongi.client.safe182.ChildSafeHousePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.safehouse.model.MapTile
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * **아동안전지킴이집**을 지도 격자 한 칸씩 받아 저장한다.
 *
 * | | |
 * |---|---|
 * | 원천 | 안전Dream 안전지도 `cl=09` |
 * | 받는 단위 | **0.1도 격자 한 칸**(약 11km) — 이 원천은 지역 코드를 안 받고 사각 범위만 받는다 |
 * | 한 번에 | 100건(원천 상한) — 한 칸이 대략 3회 |
 *
 * 전국(10,716건 = 108회)을 한 번에 받지 않는 이유는 **일일 조회 한도**가 있어서다.
 * 사용자가 실제로 가는 칸만 받아 두면 그 칸은 다시 받을 필요가 없다.
 *
 * 여성안심지킴이집과 같은 [SafetySpotType.SAFEHOUSE] 로 적재한다 — 보행자에게는 둘 다
 * "위급할 때 뛰어 들어갈 수 있는 가게"로 같은 쓸모다.
 */
@Service
class ChildSafeHouseFillService(
    private val client: ChildSafeHouseClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
    private val events: ChildSafeHouseFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(ChildSafeHouseFillService::class.java)

    /** 같은 칸을 동시에 두 번 받지 않게 막는다. 값은 쓰지 않고 열쇠만 쓴다. */
    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    fun isFilled(tile: MapTile): Boolean =
        cache.isCached(SafetySpotType.SAFEHOUSE, tile.code)

    /** 아직 안 받은 칸이면 받아서 저장하고 인덱스에 더한다. 반환 = 이번에 더한 건수. */
    fun fill(tile: MapTile): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (isFilled(tile)) {
            return 0
        }
        if (retryGate.isCoolingDown(tile.code)) {
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
            retryGate.recordFailure(tile.code)
            events.fetchFailed(tile.code)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        // 바다·산처럼 시설이 없는 칸도 있다 — 빈 채로 남겨 매번 다시 받지 않게 한다.
        cache.save(SafetySpotType.SAFEHOUSE, tile.code, points)
        if (points.isEmpty()) {
            return 0
        }
        liveStore.append(SafetySpotType.SAFEHOUSE, points)
        log.info("아동안전지킴이집 격자 채움: tile={}, 받은={}", tile.code, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<ChildSafeHousePoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafetySpotPoint(
                    type = SafetySpotType.SAFEHOUSE,
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
