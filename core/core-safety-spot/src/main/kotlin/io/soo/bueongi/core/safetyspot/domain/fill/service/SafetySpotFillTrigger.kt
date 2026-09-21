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

@Component
class SafetySpotFillTrigger(
    private val locator: RegionLocator,
    private val securityLampFill: SecurityLampFillService,
    private val streetLampFill: StreetLampFillService,
    private val policeFill: PoliceFillService,
    private val childSafeHouseFill: ChildSafeHouseFillService,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotFillTrigger::class.java)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "safety-spot-fill").apply { isDaemon = true }
    }

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
                // 예외 메시지에 인증키가 실린 URL 이 섞여 올 수 있어 causeChainOf 로 지운다.
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
        log.info("보안등 채우기 대상: 좌표 {}개 → 자치단체 {}개 {}", points.size, regions.size, regions)
        for (region in regions) {
            securityLampFill.fill(region)
        }
    }
}
