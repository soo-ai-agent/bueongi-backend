package io.soo.bueongi.core.safetyspot.domain.region.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component

@Component
class RegionLocator(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun regionCodeAt(point: GeoPoint): String? {
        for (radius in SEARCH_RADII_M) {
            val nearby: List<SafetySpotPoint> = safetySpots.index(SafetySpotType.CCTV).withinRadius(point, radius)
            val nearest: SafetySpotPoint? = nearby
                .filter { !it.sigungu.isNullOrBlank() }
                .minByOrNull { point.distanceMetersTo(it.toGeoPoint()) }
            if (nearest != null) {
                return nearest.sigungu
            }
        }
        return null
    }

    private companion object {
        val SEARCH_RADII_M: List<Double> = listOf(500.0, 3_000.0, 20_000.0)
    }
}
