package io.soo.bueongi.storage.db.core.safetyspot.store
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

class FakeSafetySpotSource(
    points: Map<SafetySpotType, List<SafetySpotPoint>> = emptyMap(),
    override val safePaths: List<SafePath> = emptyList(),
) : SafetySpotSpatialSource {
    private val indices = points.mapValues { (_, pts) -> GridSpatialIndex(pts) }

    override fun index(type: SafetySpotType): GridSpatialIndex = indices[type] ?: GridSpatialIndex(emptyList())

    companion object {
        fun of(
            type: SafetySpotType,
            points: List<GeoPoint>,
        ): FakeSafetySpotSource = FakeSafetySpotSource(mapOf(type to points.map { SafetySpotPoint(type, it.lat, it.lng) }))
    }
}
