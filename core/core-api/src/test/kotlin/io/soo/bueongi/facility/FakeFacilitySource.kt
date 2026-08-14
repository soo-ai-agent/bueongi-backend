package io.soo.bueongi.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SafePath

class FakeFacilitySource(
    points: Map<FacilityType, List<FacilityPoint>> = emptyMap(),
    override val safePaths: List<SafePath> = emptyList(),
) : FacilitySpatialSource {

    private val indices = points.mapValues { (_, pts) -> GridSpatialIndex(pts) }

    override fun index(type: FacilityType): GridSpatialIndex =
        indices[type] ?: GridSpatialIndex(emptyList())

    companion object {
        fun of(type: FacilityType, points: List<GeoPoint>): FakeFacilitySource =
            FakeFacilitySource(mapOf(type to points.map { FacilityPoint(type, it.lat, it.lng) }))
    }
}
