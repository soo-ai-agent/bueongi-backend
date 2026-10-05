package io.soo.bueongi.core.safetyspot.domain.fill.service

import io.soo.bueongi.core.common.route.RoutePlannedHandler
import io.soo.bueongi.core.safetyspot.domain.police.service.PoliceFillService
import io.soo.bueongi.core.safetyspot.domain.region.service.RegionLocator
import io.soo.bueongi.core.safetyspot.domain.safehouse.model.MapTile
import io.soo.bueongi.core.safetyspot.domain.safehouse.service.ChildSafeHouseFillService
import io.soo.bueongi.core.safetyspot.domain.securitylamp.service.SecurityLampFillService
import io.soo.bueongi.core.safetyspot.domain.streetlamp.service.StreetLampFillService
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.pickPointsAlong
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Component
class SafetySpotFillTrigger(
    private val locator: RegionLocator,
    private val securityLampFill: SecurityLampFillService,
    private val streetLampFill: StreetLampFillService,
    private val policeFill: PoliceFillService,
    private val childSafeHouseFill: ChildSafeHouseFillService,
) : RoutePlannedHandler {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotFillTrigger::class.java)

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "safety-spot-fill").apply { isDaemon = true }
        }

    override fun onRoutePlanned(points: List<GeoPoint>) {
        fillAround(points.pickPointsAlong(FILL_POINT_SPACING_METERS, MAX_FILL_POINTS))
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
                // 스택에는 인증키가 실린 원천 URL 이 딸려 온다 — 종류만 남긴다.
                log.warn(
                    "안심시설 뒤에서 채우기 실패 — 이번 채우기만 건너뜀(기존 데이터 유지) | " +
                        "원인=${e.javaClass.simpleName}",
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

    private companion object {
        const val FILL_POINT_SPACING_METERS: Double = 2_000.0

        const val MAX_FILL_POINTS: Int = 12
    }
}
