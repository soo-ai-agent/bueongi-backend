package io.soo.bueongi.core.safetyspot.domain.region.service

import io.soo.bueongi.core.safetyspot.domain.streetlight.service.StreetlightFillService
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 사용자가 닿은 좌표의 지역을 뒤에서 채운다.
 *
 * **요청 스레드를 절대 막지 않는다.** 채우기는 수십 회의 외부 호출이라 수 초가 걸리고,
 * 그것을 응답 경로에 넣으면 외부 지연이 곧 우리 지연이 된다. 이번 응답은 지금 있는 것으로 나가고,
 * 다음 요청부터 채워진 데이터가 보인다.
 */
@Component
class RegionFillTrigger(
    private val locator: RegionLocator,
    private val fillService: RegionLampFillService,
    private val streetlightFill: StreetlightFillService,
) {
    private val log: Logger = LoggerFactory.getLogger(RegionFillTrigger::class.java)

    /**
     * 채우기 전용 단일 스레드. 공용 executor 를 빌리지 않는 이유는 둘이다 —
     * 수 초짜리 외부 호출이 남의 작업을 밀어내면 안 되고, 한 번에 한 지역만 받아야
     * 여러 요청이 동시에 같은 API 를 두드리지 않는다. 데몬이라 종료를 막지 않는다.
     */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "region-fill").apply { isDaemon = true }
    }

    /** 좌표들이 속한 지역 중 아직 안 받은 곳을 뒤에서 채운다. 호출 즉시 반환한다. */
    fun fillAround(points: List<GeoPoint>) {
        if (points.isEmpty()) {
            return
        }
        executor.execute {
            try {
                fillRegionsOf(points)
            } catch (e: Exception) {
                // 채우기 실패가 사용자 요청에 영향을 주면 안 된다 — 로그만 남긴다.
                log.warn("지역 채우기 실패(무시): {}", e.message)
            }
        }
    }

    private fun fillRegionsOf(points: List<GeoPoint>) {
        // 가로등 원천은 지역 조건을 받지 않아 전국을 한 번에 받는다 — 좌표와 무관하다.
        streetlightFill.fill()
        val regions: MutableSet<String> = LinkedHashSet()
        for (point in points) {
            val code: String? = locator.regionCodeAt(point)
            if (code != null) {
                regions.add(code)
            }
        }
        for (region in regions) {
            fillService.fill(region)
        }
    }
}
