package io.soo.bueongi.core.safetyspot.domain.securitylamp.service

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.client.datagokr.securitylamp.SecurityLampClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NATIONWIDE
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class SecurityLampFillService(
    private val client: SecurityLampClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val safetySpots: SafetySpotSpatialSource,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
    private val events: SafetySpotFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(SecurityLampFillService::class.java)

    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    fun isFilled(regionCode: String): Boolean = skipReason(regionCode) != null

    private fun skipReason(regionCode: String): String? {
        if (safetySpots.index(SafetySpotType.SECURITY_LAMP).regions.contains(regionCode)) {
            return "이미 적재됨(인덱스)"
        }
        if (cache.isCached(SafetySpotType.SECURITY_LAMP, regionCode)) {
            return "이미 받아 둠(캐시)"
        }
        return null
    }

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
            events.fetchFailed(SafetySpotType.SECURITY_LAMP, regionCode)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            // 좌표를 안 주는 지자체가 있다(마포·강남 등) — 빈 캐시로 남겨 매번 다시 받지 않게 한다.
            cache.save(SafetySpotType.SECURITY_LAMP, regionCode, points)
            events.hadNoCoordinates(SafetySpotType.SECURITY_LAMP, regionCode, fetched.size)
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
