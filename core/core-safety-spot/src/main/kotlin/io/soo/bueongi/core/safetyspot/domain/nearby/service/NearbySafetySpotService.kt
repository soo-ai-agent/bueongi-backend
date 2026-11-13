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
    fun countNearby(center: GeoPoint): NearbySafetySpotsResponse {
        val countsByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
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
        }
        return NearbySafetySpotsResponse.from(countsByType)
    }

    companion object {
        // 서버 정책: "내 주변"은 반경 500m 다.
        const val NEARBY_RADIUS_METERS: Double = 500.0

        // 실측(SafetyScorer 의 같은 상수와 같은 근거): 자료가 실린 종류는 반경 1km 안에 최소 3건, 안 실린 종류는 3km 까지 0건.
        const val DATA_PRESENCE_RADIUS_METERS: Double = 1_000.0
    }
}
