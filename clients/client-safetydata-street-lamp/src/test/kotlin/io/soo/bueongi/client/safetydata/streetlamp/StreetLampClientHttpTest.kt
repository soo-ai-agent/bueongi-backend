package io.soo.bueongi.client.safetydata.streetlamp

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

class StreetLampClientHttpTest {
    private val serviceKey: String = "safetydata%2Fkey"

    private val baseUrl: String = "https://www.safetydata.go.kr"

    private fun bind(key: String = serviceKey): Pair<StreetLampClient, MockRestServiceServer> {
        val builder: RestClient.Builder = RestClient.builder()
        val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
        val client = StreetLampClient(serviceKey = key, baseUrl = baseUrl, restClientBuilder = builder)
        return client to server
    }

    @Test
    fun `정상 - EPSG5179 XMAP·YMAP 를 WGS84 로 변환하고 도로명주소·시군구를 매핑한다`() {
        val (client, server) = bind()
        val (x, y) = Epsg5179.fromWgs84(lat = 37.4981, lng = 127.0276)
        val body: String =
            """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"totalCount":1,
             "body":[{"SN":1,"FCLT_TYPE":"가로등","ADDR":"서울 강남구 역삼동 1",
                      "ROAD_NM_ADDR":"서울 강남구 테헤란로 1","STDG_SGG_CD":"11680",
                      "XMAP_CRTS":$x,"YMAP_CRTS":$y}]}
            """.trimIndent()
        server.expect(requestTo(containsString("$baseUrl/V2/api/DSSP-IF-00084?serviceKey=$serviceKey")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val streetLamps: List<StreetLampPoint> = client.fetchStreetLamps().orEmpty()

        assertThat(streetLamps).hasSize(1)
        val lamp: StreetLampPoint = streetLamps.first()
        assertThat(lamp.lat).isCloseTo(37.4981, within(1e-6))
        assertThat(lamp.lng).isCloseTo(127.0276, within(1e-6))
        assertThat(lamp.address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(lamp.sigungu).isEqualTo("11680")
        server.verify()
    }

    @Test
    fun `정상 - 경위도로 오는 좌표는 그대로 수용하고 좌표불량 행만 버린다`() {
        val (client, server) = bind()
        val body: String =
            """
            {"header":{"resultCode":"00"},"totalCount":2,
             "body":[{"SN":1,"XMAP_CRTS":127.03,"YMAP_CRTS":37.50,"ADDR":"지번주소만"},
                     {"SN":2,"XMAP_CRTS":"","YMAP_CRTS":"abc","ADDR":"좌표불량"}]}
            """.trimIndent()
        server.expect(requestTo(containsString("/V2/api/DSSP-IF-00084")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val streetLamps: List<StreetLampPoint> = client.fetchStreetLamps().orEmpty()

        assertThat(streetLamps).hasSize(1)
        assertThat(streetLamps.first().lat).isCloseTo(37.50, within(1e-9))
        assertThat(streetLamps.first().address).isEqualTo("지번주소만")
    }

    @Test
    fun `실패 - HTTP 오류면 null(기존 데이터 유지 신호)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString("/V2/api/DSSP-IF-00084")))
            .andRespond(withServerError())

        assertThat(client.fetchStreetLamps()).isNull()
    }

    @Test
    fun `실패 - resultCode 비정상이면 null`() {
        val (client, server) = bind()
        val body: String = """{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":[]}"""
        server.expect(requestTo(containsString("/V2/api/DSSP-IF-00084")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        assertThat(client.fetchStreetLamps()).isNull()
    }

    @Test
    fun `게이트 - 키 미설정이면 호출 전 차단(isEnabled=false)`() {
        val (client, _) = bind(key = "")

        assertThat(client.isEnabled).isFalse()
        assertThatThrownBy { client.fetchStreetLamps() }.isInstanceOf(IllegalStateException::class.java)
    }
}
