package io.soo.bueongi.storage.db.safetyspot.store
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

class FakeSafetySpotSource(
    points: Map<SafetySpotType, List<SafetySpotPoint>> = emptyMap(),
    override val safePaths: List<SafePath> = emptyList(),
    // API 지역 수집 출처로 칠 점들. 색인에는 points 와 합쳐 들어간다 — 실구현처럼 API 점이 앞이다.
    apiPoints: Map<SafetySpotType, List<SafetySpotPoint>> = emptyMap(),
) : SafetySpotSpatialSource {
    private val sourcesByType: Map<SafetySpotType, SafetySpotSources>

    init {
        val built = LinkedHashMap<SafetySpotType, SafetySpotSources>()
        for (type in points.keys + apiPoints.keys) {
            var sources: SafetySpotSources = SafetySpotSources.EMPTY.withFiles(points[type] ?: emptyList())
            val api: List<SafetySpotPoint>? = apiPoints[type]
            if (api != null) {
                sources = sources.withRegion("fake-region", api)
            }
            built[type] = sources
        }
        sourcesByType = built
    }

    override fun sources(type: SafetySpotType): SafetySpotSources = sourcesByType[type] ?: SafetySpotSources.EMPTY

    override fun index(type: SafetySpotType): GridSpatialIndex = sources(type).index

    companion object {
        fun of(type: SafetySpotType, points: List<GeoPoint>): FakeSafetySpotSource =
            FakeSafetySpotSource(mapOf(type to points.map { SafetySpotPoint.of(type, it.lat, it.lng) }))
    }
}
