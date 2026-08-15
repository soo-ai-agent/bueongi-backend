package io.soo.bueongi.storage.db.core.safetyspot

import io.soo.bueongi.storage.db.core.safetyspot.model.overlapMetersWith
import io.soo.bueongi.storage.db.core.safetyspot.model.pathLengthMeters
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class GeoPointTest {

    @Test
    fun `거리 - 같은 좌표는 0m`() {
        assertThat(GeoPoint(37.5, 127.0).distanceMetersTo(GeoPoint(37.5, 127.0))).isEqualTo(0.0)
    }

    @Test
    fun `거리 - 강남역에서 홍대입구역은 11231_198m (하버사인 R=6371km 기준값)`() {
        val meters = GeoPoint(37.4979, 127.0276).distanceMetersTo(GeoPoint(37.5572, 126.9245))
        assertThat(meters).isCloseTo(11_231.198, within(0.01))
    }

    @Test
    fun `거리 - 대칭성(방향 무관 동일 거리)`() {
        val ab = GeoPoint(37.4979, 127.0276).distanceMetersTo(GeoPoint(37.5133, 127.1001))
        val ba = GeoPoint(37.5133, 127.1001).distanceMetersTo(GeoPoint(37.4979, 127.0276))
        assertThat(ab).isCloseTo(ba, within(1e-6))
    }

    @Test
    fun `경로 길이는 구간 거리의 합이다`() {
        val path = listOf(GeoPoint(37.4979, 127.0276), GeoPoint(37.5133, 127.1001), GeoPoint(37.5572, 126.9245))
        val expected = path[0].distanceMetersTo(path[1]) + path[1].distanceMetersTo(path[2])
        assertThat(path.pathLengthMeters()).isCloseTo(expected, within(1e-6))
        assertThat(listOf(GeoPoint(37.5, 127.0)).pathLengthMeters()).isEqualTo(0.0)
    }

    @Test
    fun `같은 경로끼리의 겹침은 전체 길이에 가깝다`() {
        val path = listOf(GeoPoint(37.4979, 127.0276), GeoPoint(37.5133, 127.1001))
        assertThat(path.overlapMetersWith(path, 30.0)).isCloseTo(path.pathLengthMeters(), within(1.0))
    }

    @Test
    fun `멀리 떨어진 경로와는 겹치지 않는다`() {
        val seoul = listOf(GeoPoint(37.4979, 127.0276), GeoPoint(37.5133, 127.1001))
        val busan = listOf(GeoPoint(35.1796, 129.0756), GeoPoint(35.1600, 129.0600))
        assertThat(seoul.overlapMetersWith(busan, 30.0)).isEqualTo(0.0)
    }

    @Test
    fun `점에서 경로까지의 최단거리는 선분에 내린 수선이다`() {
        val path = listOf(GeoPoint(37.5000, 127.0000), GeoPoint(37.5000, 127.0100))
        val above = GeoPoint(37.5010, 127.0050)
        assertThat(above.distanceMetersToPath(path)).isLessThan(above.distanceMetersTo(path[0]))
        assertThat(above.distanceMetersToPath(emptyList())).isEqualTo(Double.POSITIVE_INFINITY)
    }
}
