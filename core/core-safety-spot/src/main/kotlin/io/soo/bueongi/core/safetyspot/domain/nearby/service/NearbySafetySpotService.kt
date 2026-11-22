package io.soo.bueongi.core.safetyspot.domain.nearby.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.api.response.NearbySafetySpotsResponse
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

@Service
class NearbySafetySpotService(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun count(center: GeoPoint): NearbySafetySpotsResponse {
        val countsByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
        val spotsByType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            if (type.markerWire == null) {
                continue
            }
            // 이 지역에 색인된 자료 자체가 없는 종류는 응답에서 뺀다 — 앱이 0 대신 "자료 없음"으로 그린다.
            val hasDataInArea: Boolean = safetySpots.index(type).withinRadius(center, DATA_PRESENCE_RADIUS_METERS).isNotEmpty()
            if (!hasDataInArea) {
                continue
            }
            val nearbySpots: List<SafetySpotPoint> = safetySpots.index(type).withinRadius(center, NEARBY_RADIUS_METERS)
            countsByType[type] = nearbySpots.size
            spotsByType[type] = closestSpots(center, nearbySpots)
        }
        return NearbySafetySpotsResponse.from(countsByType, spotsByType)
    }

    // 홈 지도에 그릴 마커 좌표 — 종류마다 가까운 순으로 상한까지만 싣는다(지도 과밀 방지).
    private fun closestSpots(center: GeoPoint, spots: List<SafetySpotPoint>): List<SafetySpotPoint> {
        if (spots.size <= MAX_SPOTS_PER_TYPE) {
            return spots
        }
        return spots.sortedBy { spot -> center.distanceMetersTo(spot.toGeoPoint()) }.take(MAX_SPOTS_PER_TYPE)
    }

    companion object {
        // 서버 정책: "내 주변"은 반경 500m 다.
        const val NEARBY_RADIUS_METERS: Double = 500.0

        // 실측(SafetyScorer 의 같은 상수와 같은 근거): 자료가 실린 종류는 반경 1km 안에 최소 3건, 안 실린 종류는 3km 까지 0건.
        const val DATA_PRESENCE_RADIUS_METERS: Double = 1_000.0

        // 홈 지도 마커의 종류별 상한 — 경로 마커 상한(RouteMarkerBuilder)과 같은 취지의 과밀 방지 값.
        const val MAX_SPOTS_PER_TYPE: Int = 10
    }
}
