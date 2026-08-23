package io.soo.bueongi.core.safetyspot.domain.fill.service

import io.soo.bueongi.core.safetyspot.domain.police.service.PoliceFillService
import io.soo.bueongi.core.safetyspot.domain.safehouse.model.MapTile
import io.soo.bueongi.core.safetyspot.domain.safehouse.service.ChildSafeHouseFillService
import io.soo.bueongi.core.safetyspot.domain.region.service.RegionLocator
import io.soo.bueongi.core.safetyspot.domain.securitylamp.service.SecurityLampFillService
import io.soo.bueongi.core.safetyspot.domain.streetlamp.service.StreetLampFillService
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.support.logging.ExceptionLogLine
import io.soo.bueongi.support.logging.IGNORED_EXCEPTION
import io.soo.bueongi.support.logging.causeChainOf
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 사용자가 닿은 곳의 안심시설 중 파일로 안 들어오는 것을 뒤에서 채운다.
 *
 * 종류마다 **받는 단위가 다르다** — 원천이 지역 조건을 받느냐에 따라 갈린다:
 * - 가로등([StreetLampFillService])·치안시설([PoliceFillService]) — 전국 한 덩이, 최초 1회
 * - 보안등([SecurityLampFillService]) — 좌표가 속한 자치단체 단위. 전국을 미리 받으면 쿼터를 넘는다
 * - 아동안전지킴이집([ChildSafeHouseFillService]) — 좌표가 속한 지도 격자 칸 단위(원천이 사각 범위만 받는다)
 *
 * **요청 스레드를 절대 막지 않는다.** 채우기는 수십 회의 외부 호출이라 수 초가 걸리고,
 * 그것을 응답 경로에 넣으면 외부 지연이 곧 우리 지연이 된다. 이번 응답은 지금 있는 것으로 나가고,
 * 다음 요청부터 채워진 데이터가 보인다.
 */
@Component
class SafetySpotFillTrigger(
    private val locator: RegionLocator,
    private val securityLampFill: SecurityLampFillService,
    private val streetLampFill: StreetLampFillService,
    private val policeFill: PoliceFillService,
    private val childSafeHouseFill: ChildSafeHouseFillService,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotFillTrigger::class.java)

    /**
     * 채우기 전용 단일 스레드. 공용 executor 를 빌리지 않는 이유는 둘이다 —
     * 수 초짜리 외부 호출이 남의 작업을 밀어내면 안 되고, 한 번에 하나만 받아야
     * 여러 요청이 동시에 같은 API 를 두드리지 않는다. 데몬이라 종료를 막지 않는다.
     */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "safety-spot-fill").apply { isDaemon = true }
    }

    /** 좌표들 주변에서 아직 안 받은 것을 뒤에서 채운다. 호출 즉시 반환한다. */
    fun fillAround(points: List<GeoPoint>) {
        if (points.isEmpty()) {
            return
        }
        executor.execute {
            try {
                fillNationwide()
                fillSecurityLampsOf(points)
                fillChildSafeHousesOf(points)
            } catch (e: Exception) {
                // 채우기 실패가 사용자 요청에 영향을 주면 안 된다 — 로그만 남긴다.
                // 메시지에는 인증키가 실린 URL 이 섞여 올 수 있어 causeChainOf 로 지운다.
                log.warn(
                    ExceptionLogLine(
                        kind = IGNORED_EXCEPTION,
                        counterpart = "안심시설",
                        subject = "뒤에서 채우기",
                        cause = causeChainOf(e),
                        action = "이번 채우기만 건너뜀(기존 데이터 유지)",
                    ).format(),
                )
            }
        }
    }

    private fun fillNationwide() {
        streetLampFill.fill()
        policeFill.fill()
    }

    private fun fillChildSafeHousesOf(points: List<GeoPoint>) {
        val tiles: MutableSet<MapTile> = LinkedHashSet()
        for (point in points) {
            tiles.add(MapTile.of(point))
        }
        for (tile in tiles) {
            childSafeHouseFill.fill(tile)
        }
    }

    private fun fillSecurityLampsOf(points: List<GeoPoint>) {
        val regions: MutableSet<String> = LinkedHashSet()
        for (point in points) {
            val code: String? = locator.regionCodeAt(point)
            if (code != null) {
                regions.add(code)
            }
        }
        if (regions.isEmpty()) {
            log.info("좌표의 자치단체를 찾지 못해 보안등 채우기를 건너뜁니다({}개 좌표)", points.size)
            return
        }
        // 좌표 수보다 자치단체 수가 외부 호출량을 정한다 — 늘어난 표본이 어디까지 번졌는지 남긴다.
        log.info("보안등 채우기 대상: 좌표 {}개 → 자치단체 {}개 {}", points.size, regions.size, regions)
        for (region in regions) {
            securityLampFill.fill(region)
        }
    }
}
