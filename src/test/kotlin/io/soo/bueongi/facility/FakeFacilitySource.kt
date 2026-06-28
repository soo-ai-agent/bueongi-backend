package io.soo.bueongi.facility

/**
 * 테스트용 가짜 [FacilitySpatialSource]. 파일 적재 없이 종류별 시설/안심귀갓길을 메모리로 주입해
 * 안전 점수·우회·마커 로직을 결정론적으로 검증한다.
 */
class FakeFacilitySource(
    points: Map<FacilityType, List<FacilityPoint>> = emptyMap(),
    override val safePaths: List<SafePath> = emptyList(),
) : FacilitySpatialSource {

    private val indices = points.mapValues { (_, pts) -> GridSpatialIndex(pts) }

    override fun index(type: FacilityType): GridSpatialIndex =
        indices[type] ?: GridSpatialIndex(emptyList())

    companion object {
        /** 한 종류의 시설만 채운 소스(간단 케이스). */
        fun of(type: FacilityType, points: List<GeoPoint>): FakeFacilitySource =
            FakeFacilitySource(mapOf(type to points.map { FacilityPoint(type, it.lat, it.lng) }))
    }
}
