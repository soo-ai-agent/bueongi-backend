package io.soo.bueongi.storage.db.core.facility

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class SpatialMathTest {

    @Test
    fun `haversine - 같은 좌표는 0m`() {
        assertThat(SpatialMath.haversineMeters(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0))).isEqualTo(0.0)
    }
    @Test
    fun `haversine - 강남역에서 홍대입구역 직선거리는 약 11 2km`() {
        val meters = SpatialMath.haversineMeters(GeoPoint(37.4979, 127.0276), GeoPoint(37.5572, 126.9245))
        assertThat(meters).isCloseTo(11_230.0, within(500.0))
    }
    @Test
    fun `haversine - 대칭성(방향 무관 동일 거리)`() {
        val ab = SpatialMath.haversineMeters(GeoPoint(37.4979, 127.0276), GeoPoint(37.5133, 127.1001))
        val ba = SpatialMath.haversineMeters(GeoPoint(37.5133, 127.1001), GeoPoint(37.4979, 127.0276))
        assertThat(ab).isCloseTo(ba, within(1e-6))
    }
}
