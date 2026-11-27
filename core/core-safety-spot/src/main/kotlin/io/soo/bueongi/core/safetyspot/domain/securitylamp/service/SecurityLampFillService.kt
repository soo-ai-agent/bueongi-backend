package io.soo.bueongi.core.safetyspot.domain.securitylamp.service

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.securitylamp.SecurityLampClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.securitylamp.SecurityLampSpot
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSource
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class SecurityLampFillService(
    private val client: SecurityLampClient,
    private val cache: SafetySpotRegionCache,
    private val safetySpotWriter: SafetySpotWriter,
    private val safetySpots: SafetySpotSource,
    private val retryGate: FillFailureCooldown = FillFailureCooldown(),
) {
    private val log: Logger = LoggerFactory.getLogger(SecurityLampFillService::class.java)

    private val inFlight: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    private fun skipReason(regionCode: String): String? {
        if (safetySpots.existsInRegion(SafetySpotType.SECURITY_LAMP, regionCode)) {
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
        val fetched: List<DataGoKrPoint>? = client.fetch(regionCode)
        if (fetched == null) {
            retryGate.record(regionCode)
            log.warn("{} 수집 실패 — 기존 데이터 유지, 쉬는 시간 뒤 다시 시도 (범위={})", SafetySpotType.SECURITY_LAMP.label, regionCode)
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        // 좌표를 안 주는 지자체가 있다(마포·강남 등) — 빈 기록해 재수집 주기 동안 다시 받지 않게 한다.
        safetySpotWriter.replaceRegion(SafetySpotType.SECURITY_LAMP, regionCode, points)
        if (points.isEmpty()) {
            log.warn("{} {}건을 받았으나 좌표가 없어 적재 0건 (범위={})", SafetySpotType.SECURITY_LAMP.label, fetched.size, regionCode)
            return 0
        }
        log.info("보안등 지역 채움: region={}, 받은={}, 좌표있음={}", regionCode, fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<DataGoKrPoint>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SecurityLampSpot(
                    lat = row.lat,
                    lng = row.lng,
                    name = row.name,
                    openLocalGovCode = row.sigungu,
                    address = row.address,
                    phone = row.phone,
                ),
            )
        }
        return points
    }
}
