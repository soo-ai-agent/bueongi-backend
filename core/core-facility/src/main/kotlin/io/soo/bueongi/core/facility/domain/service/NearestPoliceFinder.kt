package io.soo.bueongi.core.facility.domain.service

import io.soo.bueongi.core.facility.domain.model.FacilityWithDistance
import io.soo.bueongi.core.facility.domain.model.NearestPolice
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.store.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class NearestPoliceFinder(
    private val facilities: FacilitySpatialSource,
) {

    fun findNearest(center: GeoPoint, radiusM: Double?, limit: Int?): List<NearestPolice> {
        val radius: Double = (radiusM ?: DEFAULT_RADIUS_M).coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        val maxResults: Int = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return facilities.index(FacilityType.POLICE)
            .withinRadius(center, radius)
            .map { point -> FacilityWithDistance(point, center.distanceMetersTo(point.toGeoPoint())) }
            .sortedBy { it.distanceMeters }
            .take(maxResults)
            .map { found ->
                NearestPolice(
                    name = found.point.name,
                    address = found.point.address,
                    tel = found.point.phone,
                    lat = found.point.lat,
                    lng = found.point.lng,
                    distanceM = found.distanceMeters.roundToInt(),
                )
            }
    }

    companion object {
        const val DEFAULT_RADIUS_M: Double = 10_000.0
        const val MIN_RADIUS_M: Double = 100.0
        const val MAX_RADIUS_M: Double = 20_000.0
        const val DEFAULT_LIMIT: Int = 5
        const val MAX_LIMIT: Int = 10
    }
}
