package io.soo.bueongi.core.safetyspot.domain.securitylamp.service

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.client.datagokr.securitylamp.SecurityLampClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * **보안등**(골목·이면도로 방범 조명)을 자치단체 단위로 받아 저장한다.
 * 가로등은 원천도 받는 방식도 달라 [io.soo.bueongi.core.safetyspot.domain.streetlamp.service.StreetLampFillService] 가 따로 맡는다.
 *
 * | | |
 * |---|---|
 * | 원천 | data.go.kr 표준데이터 `tn_pubr_public_scrty_lmp_api` |
 * | 받는 단위 | 자치단체 하나(`instt_code`) |
 * | 한 번에 | 1,000건 — 그 지역 건수만큼 페이지를 넘긴다 |
 *
 * 전국을 미리 받지 않는 이유는 하나다 — 전량이 1,839,475건(1,840회)이라 일일 쿼터를 넘는다.
 * 대신 사용자가 실제로 가는 지역만 한 번 받아 두면, 그 지역은 영원히 다시 받을 필요가 없다.
 *
 * **요청 스레드를 막지 않는다.** 채우기는 뒤에서 돌고, 이번 응답은 지금 있는 것으로 나간다.
 */
@Service
class SecurityLampFillService(
    private val client: SecurityLampClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val safetySpots: SafetySpotSpatialSource,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
    private val events: SecurityLampFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(SecurityLampFillService::class.java)

    /** 같은 지역을 동시에 두 번 받지 않게 막는다. 값은 쓰지 않고 열쇠만 쓴다. */
    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    /**
     * 이 지역을 이미 갖고 있나.
     *
     * 인덱스를 먼저 본다 — CSV 로 이미 적재된 자치단체(창녕·김해·여주 등)를 API 로 다시 받으면
     * 같은 데이터를 위해 수십 회를 쓰게 된다. 실측으로 창녕군은 CSV 10,041건과 API 10,041건이
     * 정확히 같았다. 인덱스에 없을 때만 파일 캐시(=좌표를 안 주는 지역의 빈 기록)를 확인한다.
     */
    fun isFilled(regionCode: String): Boolean =
        skipReason(regionCode) != null

    /** 건너뛰는 이유. 받아야 하면 null. */
    private fun skipReason(regionCode: String): String? {
        if (safetySpots.index(SafetySpotType.SECURITY_LAMP).regions.contains(regionCode)) {
            return "이미 적재됨(인덱스)"
        }
        if (cache.isCached(SafetySpotType.SECURITY_LAMP, regionCode)) {
            return "이미 받아 둠(캐시)"
        }
        return null
    }

    /**
     * 아직 안 받은 지역이면 받아서 저장하고 인덱스에 더한다.
     * 반환 = 이번에 더한 건수. 이미 받았거나 키가 없으면 0.
     */
    fun fill(regionCode: String): Int {
        if (!client.isEnabled) {
            log.info("보안등 수집 건너뜀: 인증키 미설정 (region={})", regionCode)
            return 0
        }
        if (retryGate.isCoolingDown(regionCode)) {
            return 0
        }
        val skip: String? = skipReason(regionCode)
        if (skip != null) {
            log.debug("보안등 수집 건너뜀: {} (region={})", skip, regionCode)
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
            retryGate.recordFailure(regionCode)
            events.regionFetchFailed(regionCode)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            // 좌표를 안 주는 지자체가 있다(마포·강남 등) — 빈 파일로 남겨 매번 다시 받지 않게 한다.
            cache.save(SafetySpotType.SECURITY_LAMP, regionCode, points)
            events.regionHadNoCoordinates(regionCode, fetched.size)
            return 0
        }
        cache.save(SafetySpotType.SECURITY_LAMP, regionCode, points)
        liveStore.append(SafetySpotType.SECURITY_LAMP, points)
        log.info("보안등 지역 채움: region={}, 받은={}, 좌표있음={}", regionCode, fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<DataGoKrPoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafetySpotPoint(
                    type = SafetySpotType.SECURITY_LAMP,
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
