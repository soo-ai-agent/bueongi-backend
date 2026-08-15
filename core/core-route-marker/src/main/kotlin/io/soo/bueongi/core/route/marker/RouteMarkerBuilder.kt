package io.soo.bueongi.core.route.marker

import io.soo.bueongi.core.enums.FacilityPoiType

import io.soo.bueongi.support.monitoring.AlertNotifier

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.route.safety.SafetyScorer
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * 경로 주변 안심시설을 지도 마커로 만든다.
 *
 * 마커의 [FacilityPoi.x]·[FacilityPoi.y] 는 위경도가 아니라 **미니맵 안 0~100 백분율 위치**다.
 * 프론트가 지도 SDK 없이도 경로 미리보기를 그릴 수 있어야 해서, 마커 전체의 위경도 범위를
 * 정사각형에 맞춰 늘린 값을 서버가 계산해 준다.
 */
@Component
class RouteMarkerBuilder(
    private val facilities: FacilitySpatialSource,
    private val alertNotifier: AlertNotifier? = null,
) {

    fun buildMarkers(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
    ): List<FacilityPoi> {
        alertIfIndexEmpty()

        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        // x·y 는 전체 마커 범위를 알아야 정해지므로 여기서는 0 으로 두고 마지막에 한 번에 채운다.
        val raw: ArrayList<FacilityPoi> = ArrayList<FacilityPoi>()
        raw.add(FacilityPoi(type = FacilityPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(FacilityPoi(type = FacilityPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, facilityType) in MARKER_TYPES) {
            val facilities: List<FacilityPoint> =
                facilities.index(facilityType).withinCorridor(path, corridor).take(MAX_MARKERS_PER_TYPE)
            for (facility in facilities) {
                raw.add(
                    FacilityPoi(
                        type = markerType,
                        x = 0,
                        y = 0,
                        lat = facility.lat,
                        lng = facility.lng,
                        name = facility.name,
                        purpose = facility.purpose,
                        cameraCount = facility.cameraCount,
                        address = facility.address,
                        phone = facility.phone,
                    ),
                )
            }
        }

        return raw.toMiniMapPercent()
    }

    /** 마커들의 위경도 범위를 미니맵 0~100 좌표로 늘린다. 모든 마커가 한 점이면 가운데(50)에 둔다. */
    private fun List<FacilityPoi>.toMiniMapPercent(): List<FacilityPoi> {
        val minLat: Double = minOf { it.lat }
        val maxLat: Double = maxOf { it.lat }
        val minLng: Double = minOf { it.lng }
        val maxLng: Double = maxOf { it.lng }
        val spanLat: Double = maxLat - minLat
        val spanLng: Double = maxLng - minLng
        val drawableRange: Double = MINI_MAP_MAX - 2 * MARKER_PADDING
        return map { marker ->
            // 화면 y 는 아래로 증가하므로 위도는 뒤집어 넣는다(북쪽이 위).
            val x: Int = percentOrCenter(marker.lng - minLng, spanLng, drawableRange)
            val y: Int = percentOrCenter(maxLat - marker.lat, spanLat, drawableRange)
            marker.copy(x = x, y = y)
        }
    }

    private fun percentOrCenter(offset: Double, span: Double, drawableRange: Double): Int {
        if (span == 0.0) {
            return MINI_MAP_CENTER
        }
        return (MARKER_PADDING + offset / span * drawableRange).roundToInt().coerceIn(0, MINI_MAP_MAX.toInt())
    }

    private fun alertIfIndexEmpty() {
        val notifier: AlertNotifier = alertNotifier ?: return
        val totalLoaded: Int = MARKER_TYPES.sumOf { (_, facilityType) -> facilities.index(facilityType).size }
        if (totalLoaded > 0) {
            return
        }
        notifier.notify(
            key = "facility-index-empty",
            title = "안심시설 데이터 제공 실패",
            detail = "시설 인덱스 총 적재 0(적재 실패/부재) — /api/routes/facilities·/api/routes/safe-compare 의 " +
                "경로변 시설 마커를 생성할 수 없음. 데이터 적재/원천 확인 필요",
        )
    }

    private companion object {
        val MARKER_TYPES: List<Pair<FacilityPoiType, FacilityType>> = listOf(
            FacilityPoiType.CCTV to FacilityType.CCTV,
            FacilityPoiType.SAFEHOUSE to FacilityType.SAFEHOUSE,
            FacilityPoiType.BELL to FacilityType.BELL,
            FacilityPoiType.POLICE to FacilityType.POLICE,
            FacilityPoiType.LAMP to FacilityType.LAMP,
            FacilityPoiType.PEDESTRIAN_SIGNAL to FacilityType.PEDESTRIAN_SIGNAL,
        )

        const val MINI_MAP_MAX: Double = 100.0

        const val MINI_MAP_CENTER: Int = 50

        /** 미니맵 가장자리 여백(%) — 테두리에 붙은 마커가 잘려 보이지 않게 한다. */
        const val MARKER_PADDING: Double = 12.0

        /** 종류당 마커 상한. 응답 크기와 지도 가독성 때문에 자른다. */
        const val MAX_MARKERS_PER_TYPE: Int = 12
    }
}
