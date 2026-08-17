package io.soo.bueongi.client.safemap

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

class Epsg3857Test {
    @Test
    fun `서울 좌표를 왕복 변환한다`() {
        val projected: TmXy = Epsg3857.fromWgs84(lat = 37.5665, lng = 126.9780)
        val restored: Wgs84Point = Epsg3857.toWgs84(projected.x, projected.y)!!
        assertThat(restored.lat).isCloseTo(37.5665, offset(0.000001))
        assertThat(restored.lng).isCloseTo(126.9780, offset(0.000001))
    }

    @Test
    fun `가로등 실응답 좌표를 국내 WGS84로 변환한다`() {
        val point: Wgs84Point = Epsg3857.toWgs84(14_061_191.6887, 3_926_343.1907)!!
        assertThat(point.lat).isBetween(33.0, 39.0)
        assertThat(point.lng).isBetween(124.0, 132.0)
    }

    @Test
    fun `대한민국 밖 좌표는 반환하지 않는다`() {
        assertThat(Epsg3857.toWgs84(0.0, 0.0)).isNull()
    }
}
