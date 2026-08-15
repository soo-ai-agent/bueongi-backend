package io.soo.bueongi.core.domain.facility.coord

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class Epsg5179Test {

    @Test
    fun `앵커 - 투영 원점(38도, 127_5도)은 정확히 false easting-northing 으로 간다`() {
        val (x, y) = Epsg5179.fromWgs84(lat = 38.0, lng = 127.5)
        assertThat(x).isCloseTo(1_000_000.0, within(1e-3))
        assertThat(y).isCloseTo(2_000_000.0, within(1e-3))
    }

    @Test
    fun `왕복 - 한반도 주요 지점 forward-inverse 오차가 1e-7도(약 1cm) 이내`() {
        val points: List<Pair<Double, Double>> = listOf(
            37.5665 to 126.9780,
            37.4981 to 127.0276,
            35.1796 to 129.0756,
            33.4996 to 126.5312,
            38.2070 to 128.5918,
        )
        for ((lat, lng) in points) {
            val (x, y) = Epsg5179.fromWgs84(lat, lng)
            val back: Wgs84Point = Epsg5179.toWgs84(x, y)!!
            assertThat(back.lat).isCloseTo(lat, within(1e-7))
            assertThat(back.lng).isCloseTo(lng, within(1e-7))
        }
    }

    @Test
    fun `방향 - 원점 남서쪽(서울)은 x·y 모두 원점 좌표보다 작다`() {
        val (x, y) = Epsg5179.fromWgs84(lat = 37.5665, lng = 126.9780)
        assertThat(x).isLessThan(1_000_000.0)
        assertThat(y).isLessThan(2_000_000.0)
        assertThat(x).isBetween(900_000.0, 1_000_000.0)
        assertThat(y).isBetween(1_900_000.0, 2_000_000.0)
    }

    @Test
    fun `범위 - 변환 결과가 한국 범위 밖이면 null(원천 좌표 불량)`() {
        assertThat(Epsg5179.toWgs84(0.0, 0.0)).isNull()
        assertThat(Epsg5179.toWgs84(Double.NaN, 2_000_000.0)).isNull()
    }
}
