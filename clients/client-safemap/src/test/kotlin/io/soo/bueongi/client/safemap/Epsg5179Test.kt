package io.soo.bueongi.client.safemap

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * [Epsg5179] 변환 검증 — 앵커(투영 원점)로 파라미터(FE/FN·기준 위경도)를, 왕복으로 급수 자기일관성을 고정한다.
 */
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
            37.5665 to 126.9780, // 서울시청
            37.4981 to 127.0276, // 강남역
            35.1796 to 129.0756, // 부산
            33.4996 to 126.5312, // 제주
            38.2070 to 128.5918, // 속초(기준위도 북쪽)
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
        // 대략 자릿수 검증: 서울은 x 90만대, y 195만대(UTM-K 통례).
        assertThat(x).isBetween(900_000.0, 1_000_000.0)
        assertThat(y).isBetween(1_900_000.0, 2_000_000.0)
    }

    @Test
    fun `범위 - 변환 결과가 한국 범위 밖이면 null(원천 좌표 불량)`() {
        assertThat(Epsg5179.toWgs84(0.0, 0.0)).isNull()
        assertThat(Epsg5179.toWgs84(Double.NaN, 2_000_000.0)).isNull()
    }
}
