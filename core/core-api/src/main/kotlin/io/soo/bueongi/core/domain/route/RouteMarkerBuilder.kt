package io.soo.bueongi.core.domain.route

import io.soo.bueongi.support.monitoring.AlertNotifier

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.domain.facility.SafetyScorer
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class RouteMarkerBuilder(
    private val repository: FacilitySpatialSource,
    private val alertNotifier: AlertNotifier? = null,
) {

    fun buildMarkers(
        origin: GeoPoint,
        destination: GeoPoint,
        path: List<GeoPoint>,
    ): List<FacilityPoi> {
        alertIfIndexEmpty()

        val corridor: Double = SafetyScorer.DEFAULT_CORRIDOR_METERS
        val raw: ArrayList<FacilityPoi> = ArrayList<FacilityPoi>()
        raw.add(FacilityPoi(type = FacilityPoiType.START, x = 0, y = 0, lat = origin.lat, lng = origin.lng))
        raw.add(FacilityPoi(type = FacilityPoiType.END, x = 0, y = 0, lat = destination.lat, lng = destination.lng))

        for ((markerType, facilityType) in MARKER_TYPES) {
            val facilities: List<FacilityPoint> =
                repository.index(facilityType).withinCorridor(path, corridor).take(MAX_MARKERS_PER_TYPE)
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

        val minLat: Double = raw.minOf { it.lat }
        val maxLat: Double = raw.maxOf { it.lat }
        val minLng: Double = raw.minOf { it.lng }
        val maxLng: Double = raw.maxOf { it.lng }
        val range: Double = 100.0 - 2 * MARKER_PADDING
        val spanLat: Double = maxLat - minLat
        val spanLng: Double = maxLng - minLng
        return raw.map { marker ->
            val x: Int = if (spanLng == 0.0) {
                50
            } else {
                (MARKER_PADDING + (marker.lng - minLng) / spanLng * range).roundToInt()
            }
            val y: Int = if (spanLat == 0.0) {
                50
            } else {
                (MARKER_PADDING + (maxLat - marker.lat) / spanLat * range).roundToInt()
            }
            marker.copy(x = x.coerceIn(0, 100), y = y.coerceIn(0, 100))
        }
    }

    private fun alertIfIndexEmpty() {
        val notifier: AlertNotifier = alertNotifier ?: return
        val totalLoaded: Int = MARKER_TYPES.sumOf { (_, facilityType) -> repository.index(facilityType).size }
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

        const val MARKER_PADDING: Double = 12.0

        const val MAX_MARKERS_PER_TYPE: Int = 12
    }
}
