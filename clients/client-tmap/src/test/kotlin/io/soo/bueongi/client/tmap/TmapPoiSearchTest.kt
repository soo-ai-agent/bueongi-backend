package io.soo.bueongi.client.tmap

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.client.tmap.response.TmapPoiSearch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// 0건 응답은 칸을 생략하고 오고, 좌표·이름·주소도 명세상 선택(N)이라 빠질 수 있다.
class TmapPoiSearchTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `칸을 생략한 0건 응답은 빈 목록으로 읽힌다`() {
        val response: TmapPoiSearch = mapper.readValue("{}")

        assertThat(response.searchPoiInfo.pois.poi).isEmpty()
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
        val response: TmapPoiSearch = mapper.readValue(body)

        val place: TmapPlace? = response.searchPoiInfo.pois.poi.single().toTmapPlace()

        val expected = TmapPlace(name = "화목순대국 광화문1호점", address = "서울 종로구 새문안로5길 11", lat = 37.57120359, lng = 126.97482678)
        assertThat(place).isEqualTo(expected)
    }

    @Test
    fun `좌표가 빠진 POI 는 장소로 만들지 않는다`() {
        val body = """{"searchPoiInfo":{"pois":{"poi":[{"name":"이름만 있는 곳"}]}}}"""
        val response: TmapPoiSearch = mapper.readValue(body)

        assertThat(response.searchPoiInfo.pois.poi.single().toTmapPlace()).isNull()
    }

    @Test
    fun `주소 칸이 빠지면 빈 주소로 장소를 만든다`() {
        val body = """{"searchPoiInfo":{"pois":{"poi":[{"name":"강남역","frontLat":"37.49","frontLon":"127.02"}]}}}"""
        val response: TmapPoiSearch = mapper.readValue(body)

        val place: TmapPlace? = response.searchPoiInfo.pois.poi.single().toTmapPlace()

        assertThat(place).isEqualTo(TmapPlace(name = "강남역", address = "", lat = 37.49, lng = 127.02))
    }
}
