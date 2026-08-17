package io.soo.bueongi.core.safetyspot.domain.region.service

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.client.datagokr.lamp.SecurityLampClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 요청이 닿은 지역의 보안등을 그때 받아 저장한다.
 *
 * 전국을 미리 받지 않는 이유는 하나다 — 전량이 1,839,475건(1,840회)이라 일일 쿼터를 넘는다.
 * 대신 사용자가 실제로 가는 지역만 한 번 받아 두면, 그 지역은 영원히 다시 받을 필요가 없다.
 *
 * **요청 스레드를 막지 않는다.** 채우기는 뒤에서 돌고, 이번 응답은 지금 있는 것으로 나간다.
 */
@Service
class RegionLampFillService(
    private val client: SecurityLampClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val events: RegionFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(RegionLampFillService::class.java)

    /** 같은 지역을 동시에 두 번 받지 않게 막는다. 값은 쓰지 않고 열쇠만 쓴다. */
    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    fun isFilled(regionCode: String): Boolean =
        cache.isCached(SafetySpotType.LAMP, regionCode)

    /**
     * 아직 안 받은 지역이면 받아서 저장하고 인덱스에 더한다.
     * 반환 = 이번에 더한 건수. 이미 받았거나 키가 없으면 0.
     */
    fun fill(regionCode: String): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (isFilled(regionCode)) {
            return 0
        }
        if (inFlight.putIfAbsent(regionCode, true) != null) {
            return 0
        }
        return try {
            fetchAndStore(regionCode)
        } finally {
            inFlight.remove(regionCode)
        }
    }

    private fun fetchAndStore(regionCode: String): Int {
        val fetched: List<DataGoKrPoint>? = client.fetch(DataGoKrQuery(municipalityCode = regionCode))
        if (fetched == null) {
            events.regionFetchFailed(regionCode)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            // 좌표를 안 주는 지자체가 있다(마포·강남 등) — 빈 파일로 남겨 매번 다시 받지 않게 한다.
            cache.save(SafetySpotType.LAMP, regionCode, points)
            events.regionHadNoCoordinates(regionCode, fetched.size)
            return 0
        }
        cache.save(SafetySpotType.LAMP, regionCode, points)
        liveStore.append(SafetySpotType.LAMP, points)
        log.info("보안등 지역 채움: region={}, 받은={}, 좌표있음={}", regionCode, fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<DataGoKrPoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafetySpotPoint(
                    type = SafetySpotType.LAMP,
                    lat = row.lat,
                    lng = row.lng,
                    name = row.name,
                    sigungu = row.sigungu,
                    address = row.address,
                ),
            )
        }
        return points
    }
}
