package io.soo.bueongi.storage.db.core.facility.store
import io.soo.bueongi.storage.db.core.facility.model.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.model.GeoPoint
import io.soo.bueongi.storage.db.core.facility.model.SafePath
import io.soo.bueongi.core.enums.FacilityType

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
