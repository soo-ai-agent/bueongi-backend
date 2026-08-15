package io.soo.bueongi.core.safetyspot.domain.service

import io.soo.bueongi.core.safetyspot.domain.model.SafetySpotWithDistance
import io.soo.bueongi.core.safetyspot.domain.model.NearestPolice
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class NearestPoliceFinder(
    private val safetySpots: SafetySpotSpatialSource,
) {

    fun findNearest(center: GeoPoint, radiusM: Double?, limit: Int?): List<NearestPolice> {
        val radius: Double = (radiusM ?: DEFAULT_RADIUS_M).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        val maxResults: Int = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val policeIndex: GridSpatialIndex = safetySpots.index(SafetySpotType.POLICE)
        val nearbyPolice: List<SafetySpotPoint> = policeIndex.withinRadius(center, radius)

        val withDistance: ArrayList<SafetySpotWithDistance> = ArrayList<SafetySpotWithDistance>()
        for (police in nearbyPolice) {
            val distanceMeters: Double = center.distanceMetersTo(police.toGeoPoint())
            withDistance.add(SafetySpotWithDistance(police, distanceMeters))
        }
        withDistance.sortBy { it.distanceMeters }

        val nearest: ArrayList<NearestPolice> = ArrayList<NearestPolice>()
        for (found in withDistance) {
            if (nearest.size >= maxResults) {
                break
            }
            nearest.add(
                NearestPolice(
                    name = found.point.name,
                    address = found.point.address,
                    tel = found.point.phone,
                    lat = found.point.lat,
                    lng = found.point.lng,
                    distanceM = found.distanceMeters.roundToInt(),
                ),
            )
        }
        return nearest
    }

    companion object {
        const val DEFAULT_RADIUS_M: Double = 10_000.0
        const val MIN_RADIUS_M: Double = 100.0
        const val MAX_RADIUS_M: Double = 20_000.0
        const val DEFAULT_LIMIT: Int = 5
        const val MAX_LIMIT: Int = 10
    }
}
