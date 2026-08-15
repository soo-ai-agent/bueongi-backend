package io.soo.bueongi.support.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class Epsg5174Test {

    /** 파이프라인 proj4(동일 +towgs84 정의, h=0)로 뽑은 기준값 — (설명, wgs84Lat, wgs84Lng, epsg5174X, epsg5174Y). */
    private val anchors: List<Anchor> = listOf(
        Anchor("서울시청", 37.5665, 126.9780, 197986.7269, 451579.8938),
        Anchor("강남역", 37.4981, 127.0276, 202370.8770, 443988.3369),
        Anchor("부산시청", 35.1796, 129.0756, 389004.1526, 188683.8461),
        Anchor("제주시청", 33.4996, 126.5312, 156361.0803, 451.0668),
        Anchor("속초", 38.2070, 128.5918, 339354.7647, 523867.7674),
    )

    @Test
    fun `기준 - proj4 로 만든 5174 좌표가 원 WGS84 로 복원된다(datum 규약 고정)`() {
        for (anchor in anchors) {
            val result: Wgs84Point? = Epsg5174.toWgs84(anchor.x, anchor.y)
            assertThat(result).describedAs(anchor.name).isNotNull
            assertThat(result!!.lat).describedAs("${anchor.name} lat").isCloseTo(anchor.lat, within(1e-4))
            assertThat(result.lng).describedAs("${anchor.name} lng").isCloseTo(anchor.lng, within(1e-4))
        }
    }

    @Test
    fun `왕복 - WGS84 forward-inverse 오차가 1e-6도(약 10cm) 이내`() {
        for (anchor in anchors) {
            val (x, y) = Epsg5174.fromWgs84(anchor.lat, anchor.lng)
            val back: Wgs84Point = Epsg5174.toWgs84(x, y)!!
            assertThat(back.lat).describedAs("${anchor.name} 왕복 lat").isCloseTo(anchor.lat, within(1e-6))
            assertThat(back.lng).describedAs("${anchor.name} 왕복 lng").isCloseTo(anchor.lng, within(1e-6))
        }
    }

    @Test
    fun `방향 - fromWgs84 가 proj4 기준 x·y 와 일치한다(정방향도 같은 규약)`() {
        for (anchor in anchors) {
            val (x, y) = Epsg5174.fromWgs84(anchor.lat, anchor.lng)
            assertThat(x).describedAs("${anchor.name} x").isCloseTo(anchor.x, within(1.0))
            assertThat(y).describedAs("${anchor.name} y").isCloseTo(anchor.y, within(1.0))
        }
    }

    @Test
    fun `범위 - 변환 결과가 한국 범위 밖이거나 비유한값이면 null`() {
        assertThat(Epsg5174.toWgs84(2_000_000.0, 0.0)).isNull()
        assertThat(Epsg5174.toWgs84(0.0, -2_000_000.0)).isNull()
        assertThat(Epsg5174.toWgs84(Double.NaN, 500000.0)).isNull()
    }

    private data class Anchor(
        val name: String,
        val lat: Double,
        val lng: Double,
        val x: Double,
        val y: Double,
    )
}
