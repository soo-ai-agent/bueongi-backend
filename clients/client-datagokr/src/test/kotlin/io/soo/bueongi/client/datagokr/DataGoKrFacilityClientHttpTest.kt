package io.soo.bueongi.client.datagokr

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class DataGoKrFacilityClientHttpTest {

    private val serviceKey: String = "enc%2Ftest-key"

    private fun bind(key: String = serviceKey): Pair<DataGoKrFacilityClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return DataGoKrFacilityClient(serviceKey = key, restClientBuilder = builder) to server
    }

    private fun cctvItem(lat: Any = 37.5, lng: Any = 127.0, name: String = "관리기관") =
        """{"WGS84_LAT":"$lat","WGS84_LOT":"$lng","MNG_INST_NM":"$name","LCTN_ROAD_NM_ADDR":"서울시 도로명 1"}"""

    private fun envelope(items: String, totalCount: Int) =
        """{"response":{"header":{"resultCode":"0","resultMsg":"정상"},"body":{"items":[$items],"totalCount":$totalCount}}}"""

    @Test
    fun `정상 - serviceKey 원형 유지 + 페이징 파라미터 + 필드 매핑(CCTV)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString("${DataGoKrFacilityClient.CCTV_ENDPOINT}?serviceKey=$serviceKey")))
            .andExpect(requestTo(containsString("pageNo=1&numOfRows=100&type=json")))
            .andRespond(withSuccess(envelope(cctvItem(), 1), MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = client.fetchCctv()

        assertThat(points).hasSize(1)
        assertThat(points!![0].lat).isEqualTo(37.5)
        assertThat(points[0].lng).isEqualTo(127.0)
        assertThat(points[0].name).isEqualTo("관리기관")
        assertThat(points[0].address).isEqualTo("서울시 도로명 1")
        server.verify()
    }

    @Test
    fun `정상 - properties 파일에서 포함된 인증키 따옴표를 제거한다`() {
        val (client, server) = bind(key = "'$serviceKey'")
        server.expect(requestTo(containsString("serviceKey=$serviceKey")))
            .andRespond(withSuccess(envelope(cctvItem(), 1), MediaType.APPLICATION_JSON))

        assertThat(client.fetchCctv()).hasSize(1)
        server.verify()
    }

    @Test
    fun `정상 - 가득 찬 페이지는 다음 페이지로 순회하고 마지막 페이지에서 멈춘다`() {
        val (client, server) = bind()
        val fullPage: String = (1..DataGoKrFacilityClient.PAGE_SIZE).joinToString(",") { cctvItem(name = "기관$it") }
        val totalCount: Int = DataGoKrFacilityClient.PAGE_SIZE + 2
        server.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(envelope(fullPage, totalCount), MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("pageNo=2")))
            .andRespond(
                withSuccess(
                    envelope("${cctvItem(name = "기관A")},${cctvItem(name = "기관B")}", totalCount),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = client.fetchCctv()

        assertThat(points).hasSize(DataGoKrFacilityClient.PAGE_SIZE + 2)
        server.verify()
    }

    @Test
    fun `정상 - items가 item 래퍼 형태여도 수용하고, 좌표 불량 행은 건너뛴다(비상벨 필드 매핑)`() {
        val (client, server) = bind()
        val body: String = """
            {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
              {"WGS84_LAT":"37.51","WGS84_LOT":"127.02","INSTL_PSTN":"신문초삼거리","MNG_INST_NM":"강남구청","LCTN_ROAD_NM_ADDR":"강남대로 1"},
              {"WGS84_LAT":"","WGS84_LOT":"127.02","INSTL_PSTN":"좌표없음"},
              {"WGS84_LAT":"not-a-number","WGS84_LOT":"127.02","INSTL_PSTN":"좌표불량"}
            ]},"totalCount":3}}}
        """.trimIndent()
        server.expect(requestTo(containsString(DataGoKrFacilityClient.BELL_ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = client.fetchEmergencyBells()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("신문초삼거리")
        assertThat(points[0].address).isEqualTo("강남대로 1")
    }

    @Test
    fun `정상 - 여성안심지킴이집 표준 필드(storNm·latitude·rdnmadr) 매핑`() {
        val (client, server) = bind()
        val body: String = """
            {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[
              {"storNm":"GS25 강남점","latitude":"37.498","longitude":"127.027","rdnmadr":"강남대로 396"}
            ]},"totalCount":1}}
        """.trimIndent()
        server.expect(requestTo(containsString(DataGoKrFacilityClient.SAFEHOUSE_ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = client.fetchSafehouses()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("GS25 강남점")
        assertThat(points[0].lat).isEqualTo(37.498)
        assertThat(points[0].address).isEqualTo("강남대로 396")
    }

    @Test
    fun `정상 - 여성안심지킴이집 한글 컬럼 응답도 후보 폴백으로 매핑`() {
        val (client, server) = bind()
        val body: String = envelope(
            """{"업소명":"세븐일레븐 서초점","위도":"37.49","경도":"127.01","소재지도로명주소":"서초대로 11"}""",
            1,
        )
        server.expect(requestTo(containsString(DataGoKrFacilityClient.SAFEHOUSE_ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = client.fetchSafehouses()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("세븐일레븐 서초점")
        assertThat(points[0].lng).isEqualTo(127.01)
        assertThat(points[0].address).isEqualTo("서초대로 11")
    }

    @Test
    fun `에러 - resultCode가 00이 아니면 null(기존 데이터 유지 신호)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString(DataGoKrFacilityClient.CCTV_ENDPOINT)))
            .andRespond(
                withSuccess(
                    """{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"},"body":{}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(client.fetchCctv()).isNull()
    }

    @Test
    fun `에러 - HTTP 5xx면 null(부분수집으로 교체하지 않음)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString(DataGoKrFacilityClient.CCTV_ENDPOINT)))
            .andRespond(withServerError())

        assertThat(client.fetchCctv()).isNull()
    }

    @Test
    fun `에러 - 키 미설정이면 isEnabled=false 이고 호출은 계약 위반이다`() {
        val (client, _) = bind(key = " ")

        assertThat(client.isEnabled).isFalse()
        assertThatThrownBy { client.fetchCctv() }.isInstanceOf(IllegalStateException::class.java)
    }
}
