package io.soo.bueongi.core.safetyspot.domain.police.service

import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPolicePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * **치안시설**(지구대·파출소·치안센터·경찰서)을 처음 필요할 때 한 번 받아 저장한다.
 *
 * | | |
 * |---|---|
 * | 원천 | 생활안전지도 `IF_0036` |
 * | 받는 단위 | **전국 한 덩이** — 이 API 는 지역 조건을 받지 않는다 |
 * | 한 번에 | 1,000건 — 전량이 3,064건이라 4페이지로 끝난다 |
 *
 * 원천 갱신주기가 1년이라 한 번 받아 두면 오래 쓴다. 지역 코드 자리에 [NATIONWIDE] 하나를 쓰고
 * 저장·만료 규칙은 조명과 똑같이 캐시에 맡긴다. 기동 때 받지 않는 이유는 부팅을 외부 API 지연에
 * 묶지 않기 위해서다.
 */
@Service
class PoliceFillService(
    private val client: SafemapPoliceClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val events: PoliceFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(PoliceFillService::class.java)

    /** 동시에 두 번 받지 않게 막는다. */
    private val inFlight: AtomicBoolean = AtomicBoolean(false)

    fun isFilled(): Boolean =
        cache.isCached(SafetySpotType.POLICE, NATIONWIDE)

    /** 아직 안 받았으면 받아서 저장하고 인덱스에 더한다. 반환 = 이번에 더한 건수. */
    fun fill(): Int {
        if (!client.isEnabled) {
            return 0
        }
        if (isFilled()) {
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
            events.fetchFailed()
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            cache.save(SafetySpotType.POLICE, NATIONWIDE, points)
            events.hadNoCoordinates(fetched.size)
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

    private companion object {
        /** 이 원천은 전국을 한 덩이로만 주므로 지역 코드 자리에 이 값을 쓴다. */
        const val NATIONWIDE: String = "nationwide"
    }
}
