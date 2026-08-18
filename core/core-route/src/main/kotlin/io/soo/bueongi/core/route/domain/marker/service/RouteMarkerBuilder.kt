package io.soo.bueongi.core.route.domain.marker.service

import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoi
import io.soo.bueongi.core.enums.SafetySpotPoiType

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillTrigger
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component
import kotlin.math.roundToInt
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex

/**
 * 경로 주변 안심시설을 지도 마커로 만든다.
 *
 * 마커의 [SafetySpotPoi.x]·[SafetySpotPoi.y] 는 위경도가 아니라 **미니맵 안 0~100 백분율 위치**다.
 * 프론트가 지도 SDK 없이도 경로 미리보기를 그릴 수 있어야 해서, 마커 전체의 위경도 범위를
 * 정사각형에 맞춰 늘린 값을 서버가 계산해 준다.
 */
@Component
class RouteMarkerBuilder(
    private val safetySpots: SafetySpotSpatialSource,
    // 이것 없이도 본 기능이 돌아야 해서 null 이 가능함
    private val markerEvents: MarkerEventPublisher? = null,
    // 지역 채우기를 안 쓰는 구성(테스트 등)에서 null 이 가능함
    private val regionFill: SafetySpotFillTrigger? = null,
) {

    fun buildMarkers(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
    ): List<SafetySpotPoi> {
        alertIfIndexEmpty()
        // 이번 응답은 지금 있는 것으로 나가고, 안 받은 지역은 뒤에서 채워 다음 요청부터 보인다.
        regionFill?.fillAround(listOf(origin, destination))

        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        // x·y 는 전체 마커 범위를 알아야 정해지므로 여기서는 0 으로 두고 마지막에 한 번에 채운다.
        val raw: ArrayList<SafetySpotPoi> = ArrayList()
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(SafetySpotPoi(type = SafetySpotPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, safetySpotType) in MARKER_TYPES) {
            val typeIndex: GridSpatialIndex = safetySpots.index(safetySpotType)
            val alongPath: List<SafetySpotPoint> = typeIndex.withinCorridor(path, corridor)
            val shown: List<SafetySpotPoint> = alongPath.take(MAX_MARKERS_PER_TYPE)
            for (safetySpot in shown) {
                raw.add(
                    SafetySpotPoi(
                        type = markerType,
                        x = 0,
                        y = 0,
                        lat = safetySpot.lat,
                        lng = safetySpot.lng,
                        name = safetySpot.name,
                        purpose = safetySpot.purpose,
                        cameraCount = safetySpot.cameraCount,
                        address = safetySpot.address,
                        phone = safetySpot.phone,
                    ),
                )
            }
        }

        return raw.toMiniMapPercent()
    }

    /** 마커들의 위경도 범위를 미니맵 0~100 좌표로 늘린다. 모든 마커가 한 점이면 가운데(50)에 둔다. */
    private fun List<SafetySpotPoi>.toMiniMapPercent(): List<SafetySpotPoi> {
        val minLat: Double = minOf { it.lat }
        val maxLat: Double = maxOf { it.lat }
        val minLng: Double = minOf { it.lng }
        val maxLng: Double = maxOf { it.lng }
        val spanLat: Double = maxLat - minLat
        val spanLng: Double = maxLng - minLng
        val drawableRange: Double = MINI_MAP_MAX - 2 * MARKER_PADDING
        val placed: ArrayList<SafetySpotPoi> = ArrayList()
        for (marker in this) {
            // 화면 y 는 아래로 증가하므로 위도는 뒤집어 넣는다(북쪽이 위).
            val x: Int = percentOrCenter(marker.lng - minLng, spanLng, drawableRange)
            val y: Int = percentOrCenter(maxLat - marker.lat, spanLat, drawableRange)
            placed.add(marker.copy(x = x, y = y))
        }
        return placed
    }

    private fun percentOrCenter(offset: Double, span: Double, drawableRange: Double): Int {
        if (span == 0.0) {
            return MINI_MAP_CENTER
        }
        val ratio: Double = offset / span
        val percent: Double = MARKER_PADDING + ratio * drawableRange
        return percent.roundToInt().coerceIn(0, MINI_MAP_MAX.toInt())
    }

    private fun alertIfIndexEmpty() {
        val publisher: MarkerEventPublisher = markerEvents ?: return
        val totalLoaded: Int = MARKER_TYPES.sumOf { (_, safetySpotType) -> safetySpots.index(safetySpotType).size }
        if (totalLoaded > 0) {
            return
        }
        publisher.safetySpotIndexEmpty(
            "시설 인덱스 총 적재 0(적재 실패/부재) — /api/routes/safetySpots·/api/routes/safe-compare 의 " +
                "경로변 시설 마커를 생성할 수 없음. 데이터 적재/원천 확인 필요",
        )
    }

    private companion object {
        val MARKER_TYPES: List<Pair<SafetySpotPoiType, SafetySpotType>> = listOf(
            SafetySpotPoiType.CCTV to SafetySpotType.CCTV,
            SafetySpotPoiType.SAFEHOUSE to SafetySpotType.SAFEHOUSE,
            SafetySpotPoiType.BELL to SafetySpotType.BELL,
            SafetySpotPoiType.POLICE to SafetySpotType.POLICE,
            SafetySpotPoiType.STREET_LAMP to SafetySpotType.STREET_LAMP,
            SafetySpotPoiType.SECURITY_LAMP to SafetySpotType.SECURITY_LAMP,
            SafetySpotPoiType.PEDESTRIAN_SIGNAL to SafetySpotType.PEDESTRIAN_SIGNAL,
        )

        const val MINI_MAP_MAX: Double = 100.0
        const val MINI_MAP_CENTER: Int = 50

        /** 미니맵 가장자리 여백(%) — 테두리에 붙은 마커가 잘려 보이지 않게 한다. */
        const val MARKER_PADDING: Double = 12.0

        /** 종류당 마커 상한. 응답 크기와 지도 가독성 때문에 자른다. */
        const val MAX_MARKERS_PER_TYPE: Int = 12
    }
}
