package io.soo.bueongi.core.safetyspot.domain.streetlight.service

import io.soo.bueongi.client.safetydata.SafetydataStreetlight
import io.soo.bueongi.client.safetydata.SafetydataStreetlightClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 가로등(재난안전데이터 DSSP-IF-00084)을 처음 필요할 때 한 번 받아 저장한다.
 *
 * 보안등과 달리 **지역별로 나눠 받을 수 없다** — 이 API 는 지역 조건을 받지 않는다.
 * 대신 전량이 2페이지(1,000건씩)뿐이라 한 번에 받아도 싸다. 그래서 지역 코드 자리에
 * [NATIONWIDE] 하나를 쓰고, 나머지 저장·만료 규칙은 보안등과 똑같이 캐시에 맡긴다.
 *
 * 기동 때 받지 않는 이유는 부팅을 외부 API 지연에 묶지 않기 위해서다.
 */
@Service
class StreetlightFillService(
    private val client: SafetydataStreetlightClient,
    private val cache: SafetySpotRegionCache,
    private val liveStore: SafetySpotLiveStore,
    private val events: StreetlightFillEventPublisher,
) {
    private val log: Logger = LoggerFactory.getLogger(StreetlightFillService::class.java)

    /** 동시에 두 번 받지 않게 막는다. */
    private val inFlight: AtomicBoolean = AtomicBoolean(false)

    fun isFilled(): Boolean =
        cache.isCached(SafetySpotType.LAMP, NATIONWIDE)

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
        val fetched: List<SafetydataStreetlight>? = client.fetchStreetlights()
        if (fetched == null) {
            events.fetchFailed()
            return 0
        }
        val points: List<SafetySpotPoint> = toSafetySpots(fetched)
        if (points.isEmpty()) {
            cache.save(SafetySpotType.LAMP, NATIONWIDE, points)
            events.hadNoCoordinates(fetched.size)
            return 0
        }
        cache.save(SafetySpotType.LAMP, NATIONWIDE, points)
        liveStore.append(SafetySpotType.LAMP, points)
        log.info("가로등 채움: 받은={}, 좌표있음={}", fetched.size, points.size)
        return points.size
    }

    private fun toSafetySpots(fetched: List<SafetydataStreetlight>): List<SafetySpotPoint> {
        val points: ArrayList<SafetySpotPoint> = ArrayList(fetched.size)
        for (row in fetched) {
            points.add(
                SafetySpotPoint(
                    type = SafetySpotType.LAMP,
                    lat = row.lat,
                    lng = row.lng,
                    sigungu = row.sigungu,
                    address = row.address,
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
