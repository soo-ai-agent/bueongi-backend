package io.soo.bueongi.client.safemap

import io.soo.bueongi.support.util.Epsg5179

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class SafetydataStreetlightClientHttpTest {

    private val serviceKey: String = "safetydata%2Fkey"

    private fun bind(key: String = serviceKey): Pair<SafetydataStreetlightClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return SafetydataStreetlightClient(serviceKey = key, restClientBuilder = builder) to server
    }

    @Test
    fun `정상 - EPSG5179 XMAP·YMAP 를 WGS84 로 변환하고 도로명주소·시군구를 매핑한다`() {
        val (client, server) = bind()
        val (x, y) = Epsg5179.fromWgs84(lat = 37.4981, lng = 127.0276)
        val body: String = """
            {"resultCode":"00","resultMsg":"NORMAL","totalCount":1,
             "body":[{"SN":"1","FCLT_TYPE":"가로등","ADDR":"서울 강남구 역삼동 1",
                      "ROAD_NM_ADDR":"서울 강남구 테헤란로 1","STDG_SGG_CD":"11680",
                      "XMAP_CRTS":"$x","YMAP_CRTS":"$y"}]}
        """.trimIndent()
        server.expect(requestTo(containsString("${SafetydataApiSpec.ENDPOINT}?serviceKey=$serviceKey")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val streetlights: List<SafetydataStreetlight>? = client.fetchStreetlights()

        assertThat(streetlights).hasSize(1)
        val s: SafetydataStreetlight = streetlights!![0]
        assertThat(s.lat).isCloseTo(37.4981, within(1e-6))
        assertThat(s.lng).isCloseTo(127.0276, within(1e-6))
        assertThat(s.address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(s.sigungu).isEqualTo("11680")
        server.verify()
    }

    @Test
    fun `정상 - 경위도(WGS84)로 오는 좌표는 그대로 수용하고 좌표불량 행은 버린다`() {
        val (client, server) = bind()
        val body: String = """
            {"resultCode":"00","totalCount":2,
             "body":[{"SN":"1","XMAP_CRTS":"127.03","YMAP_CRTS":"37.50","ADDR":"지번주소만"},
                     {"SN":"2","XMAP_CRTS":"","YMAP_CRTS":"abc","ADDR":"좌표불량"}]}
        """.trimIndent()
        server.expect(requestTo(containsString(SafetydataApiSpec.ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val streetlights: List<SafetydataStreetlight>? = client.fetchStreetlights()

        assertThat(streetlights).hasSize(1)
        assertThat(streetlights!![0].lat).isCloseTo(37.50, within(1e-9))
        assertThat(streetlights[0].address).isEqualTo("지번주소만")
    }

    @Test
    fun `실패 - HTTP 오류면 null(기존 데이터 유지 신호)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString(SafetydataApiSpec.ENDPOINT)))
            .andRespond(withServerError())

        assertThat(client.fetchStreetlights()).isNull()
    }

    @Test
    fun `실패 - resultCode 비정상이면 null`() {
        val (client, server) = bind()
        val body: String = """{"resultCode":"03","resultMsg":"NODATA_ERROR","body":[]}"""
        server.expect(requestTo(containsString(SafetydataApiSpec.ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        assertThat(client.fetchStreetlights()).isNull()
    }

    @Test
    fun `게이트 - 키 미설정이면 호출 전 차단(isEnabled=false)`() {
        val (client, _) = bind(key = "")
        assertThat(client.isEnabled).isFalse()
        assertThatThrownBy { client.fetchStreetlights() }.isInstanceOf(IllegalStateException::class.java)
    }
}
