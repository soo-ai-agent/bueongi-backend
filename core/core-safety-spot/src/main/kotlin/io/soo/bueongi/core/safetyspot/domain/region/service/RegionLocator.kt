package io.soo.bueongi.core.safetyspot.domain.region.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.cctv.CctvSpot
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component

@Component
class RegionLocator(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun locate(point: GeoPoint): String? {
        for (radius in SEARCH_RADII_M) {
            val nearby: List<SafetySpotPoint> = safetySpots.index(SafetySpotType.CCTV).withinRadius(point, radius)
            val code: String? = nearestCodeOf(point, nearby)
            if (code != null) {
                return code
            }
        }
        return null
    }

    private fun nearestCodeOf(point: GeoPoint, nearby: List<SafetySpotPoint>): String? {
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
        return nearest?.openLocalGovCode
    }

    private companion object {
        val SEARCH_RADII_M: List<Double> = listOf(500.0, 3_000.0, 20_000.0)
    }
}
