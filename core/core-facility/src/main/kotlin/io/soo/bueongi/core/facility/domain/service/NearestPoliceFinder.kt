package io.soo.bueongi.core.facility.domain.service

import io.soo.bueongi.core.facility.domain.model.FacilityWithDistance
import io.soo.bueongi.core.facility.domain.model.NearestPolice
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.model.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.store.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.store.GridSpatialIndex
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
        val policeIndex: GridSpatialIndex = facilities.index(FacilityType.POLICE)
        val nearbyPolice: List<FacilityPoint> = policeIndex.withinRadius(center, radius)

        val withDistance: ArrayList<FacilityWithDistance> = ArrayList<FacilityWithDistance>()
        for (police in nearbyPolice) {
            val distanceMeters: Double = center.distanceMetersTo(police.toGeoPoint())
            withDistance.add(FacilityWithDistance(police, distanceMeters))
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
