package io.soo.bueongi.client.tmap.place

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.place.Place
import io.soo.bueongi.client.tmap.place.response.parseTmapPoiSearch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// 좌표·이름·주소는 명세상 선택(N)이라 빠질 수 있다.
class TmapPoiSearchTest {
    private val mapper = ObjectMapper()

    @Test
    fun `칸을 생략한 응답은 빈 목록으로 읽힌다`() {
        val places: List<Place> = parseTmapPoiSearch(mapper.readTree("{}"))

        assertThat(places).isEmpty()
    }

    @Test
    fun `실응답 꼴의 POI 를 장소로 바꾼다 - 좌표가 문자열로 와도 숫자로 읽는다`() {
        val body =
            """
            {"searchPoiInfo":{"pois":{"poi":[{
                "name":"화목순대국 광화문1호점",
                "frontLat":"37.57120359","frontLon":"126.97482678",
                "newAddressList":{"newAddress":[{"fullAddressRoad":"서울 종로구 새문안로5길 11"}]}
            }]}}}
            """

        val places: List<Place> = parseTmapPoiSearch(mapper.readTree(body))

        val expected = Place(name = "화목순대국 광화문1호점", address = "서울 종로구 새문안로5길 11", lat = 37.57120359, lng = 126.97482678)
        assertThat(places).containsExactly(expected)
    }

    @Test
    fun `좌표가 빠진 POI 는 장소로 만들지 않는다`() {
        val body = """{"searchPoiInfo":{"pois":{"poi":[{"name":"이름만 있는 곳"}]}}}"""

        val places: List<Place> = parseTmapPoiSearch(mapper.readTree(body))

        assertThat(places).isEmpty()
    }

    @Test
    fun `이름이 null 인 POI 는 장소로 만들지 않는다`() {
        val body = """{"searchPoiInfo":{"pois":{"poi":[{"name":null,"frontLat":"37.49","frontLon":"127.02"}]}}}"""

        val places: List<Place> = parseTmapPoiSearch(mapper.readTree(body))

        assertThat(places).isEmpty()
    }

    @Test
    fun `주소 칸이 빠지면 빈 주소로 장소를 만든다`() {
        val body = """{"searchPoiInfo":{"pois":{"poi":[{"name":"강남역","frontLat":"37.49","frontLon":"127.02"}]}}}"""

        val places: List<Place> = parseTmapPoiSearch(mapper.readTree(body))

        assertThat(places).containsExactly(Place(name = "강남역", address = "", lat = 37.49, lng = 127.02))
    }
}
