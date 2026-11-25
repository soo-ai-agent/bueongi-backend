package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.cctv.CctvSpot
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import org.springframework.stereotype.Component

// 좌표가 속한 자치단체(개방자치단체코드 7자리)를 가장 가까운 CCTV 로 찾는다 — API 채우기와 업로드가 같은 기준을 쓴다.
@Component
class RegionLocator(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun locate(point: GeoPoint): String? {
        for (radius in SEARCH_RADII_M) {
            val nearby: List<SafetySpotPoint> = safetySpots.index(SafetySpotType.CCTV).withinRadius(point, radius)
            var nearest: CctvSpot? = null
            var nearestDistance: Double = Double.MAX_VALUE
            for (spot in nearby) {
                if (spot !is CctvSpot || spot.openLocalGovCode.isNullOrBlank()) {
                    continue
                }
                val distance: Double = point.distanceMetersTo(spot.toGeoPoint())
                if (distance < nearestDistance) {
                    nearest = spot
                    nearestDistance = distance
                }
            }
            if (nearest != null) {
                return nearest.openLocalGovCode
            }
        }
        return null
    }

    private companion object {
        val SEARCH_RADII_M: List<Double> = listOf(500.0, 3_000.0, 20_000.0)
    }
}
