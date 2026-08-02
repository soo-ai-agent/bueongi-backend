package io.soo.bueongi.storage.db.core.facility
import io.soo.bueongi.core.enums.FacilityType

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [GridSpatialIndex] 단위 테스트. 버킷은 후보만 좁히고 최종 포함은 정확한 거리로 거른다 —
 * 반경/회랑 질의가 셀 경계와 무관하게 정확한 결과를 내는지, 빈 인덱스 방어를 고정한다.
 */
class GridSpatialIndexTest {

    private fun cctv(lat: Double, lng: Double) = FacilityPoint(FacilityType.CCTV, lat, lng)

    @Test
    fun `withinRadius - 반경 안만 반환(셀 경계 무관)`() {
        val near = cctv(37.5001, 127.0001)
        val far = cctv(37.6, 127.1)
        val index = GridSpatialIndex(listOf(near, far))
        val found = index.withinRadius(GeoPoint(37.5, 127.0), 100.0)
        assertThat(found).containsExactly(near)
    }

    @Test
    fun `withinRadius - 셀 경계를 넘는 이웃도 누락 없이 찾는다`() {
        val center: GeoPoint = GeoPoint(37.50049, 127.0)
        val acrossCell = cctv(37.5013, 127.0)
        val index = GridSpatialIndex(listOf(acrossCell))
        assertThat(index.withinRadius(center, 200.0)).contains(acrossCell)
    }

    @Test
    fun `withinCorridor - 회랑 안 점만(점-선분 거리)`() {
        val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.01))
        val onPath = cctv(37.5, 127.005)
        val offPath = cctv(37.5020, 127.005)
        val index = GridSpatialIndex(listOf(onPath, offPath))
        assertThat(index.withinCorridor(path, 30.0)).containsExactly(onPath)
    }

    @Test
    fun `빈 인덱스와 비유효 반경 방어`() {
        val empty = GridSpatialIndex(emptyList())
        assertThat(empty.size).isZero()
        assertThat(empty.withinRadius(GeoPoint(37.5, 127.0), 100.0)).isEmpty()
        assertThat(empty.withinCorridor(listOf(GeoPoint(37.5, 127.0)), 30.0)).isEmpty()
        val one = GridSpatialIndex(listOf(cctv(37.5, 127.0)))
        assertThat(one.withinRadius(GeoPoint(37.5, 127.0), 0.0)).isEmpty()
    }
}
