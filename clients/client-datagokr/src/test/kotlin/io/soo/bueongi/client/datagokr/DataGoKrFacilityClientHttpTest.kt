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

/**
 * [DataGoKrFacilityClient] 의 실 HTTP 계층 테스트 — [MockRestServiceServer] 를 [RestClient.Builder] 에
 * 바인딩해 요청 URL(serviceKey 원형 유지·페이징 파라미터)과 응답 파싱(페이지 순회·items 형태 정규화·
 * 불량 행 skip), 실패 의미론(HTTP 오류/resultCode ≠ 00 → null)을 고정한다.
 */
class DataGoKrFacilityClientHttpTest {

    /** Encoding 키(이미 URL-인코딩된 형태)를 그대로 쓰는지 검증하기 위해 %2F 를 포함시킨다. */
    private val serviceKey = "enc%2Ftest-key"

    private fun bind(key: String = serviceKey): Pair<DataGoKrFacilityClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return DataGoKrFacilityClient(serviceKey = key, restClientBuilder = builder) to server
    }

    private fun cctvItem(lat: Any = 37.5, lng: Any = 127.0, name: String = "관리기관") =
        """{"latitude":"$lat","longitude":"$lng","institutionNm":"$name","rdnmadr":"서울시 도로명 1"}"""

    private fun envelope(items: String, totalCount: Int) =
        """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL"},"body":{"items":[$items],"totalCount":$totalCount}}}"""

    @Test
    fun `정상 - serviceKey 원형 유지 + 페이징 파라미터 + 필드 매핑(CCTV)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString("${DataGoKrFacilityClient.CCTV_ENDPOINT}?serviceKey=$serviceKey")))
            .andExpect(requestTo(containsString("pageNo=1&numOfRows=1000&type=json")))
            .andRespond(withSuccess(envelope(cctvItem(), 1), MediaType.APPLICATION_JSON))

        val points = client.fetchCctv()

        assertThat(points).hasSize(1)
        assertThat(points!![0].lat).isEqualTo(37.5)
        assertThat(points[0].lng).isEqualTo(127.0)
        assertThat(points[0].name).isEqualTo("관리기관")
        assertThat(points[0].address).isEqualTo("서울시 도로명 1")
        server.verify()
    }

    @Test
    fun `정상 - 가득 찬 페이지는 다음 페이지로 순회하고 마지막 페이지에서 멈춘다`() {
        val (client, server) = bind()
        val fullPage = (1..DataGoKrFacilityClient.PAGE_SIZE).joinToString(",") { cctvItem(name = "기관$it") }
        server.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(envelope(fullPage, 1002), MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("pageNo=2")))
            .andRespond(withSuccess(envelope("${cctvItem(name = "기관A")},${cctvItem(name = "기관B")}", 1002), MediaType.APPLICATION_JSON))

        val points = client.fetchCctv()

        assertThat(points).hasSize(DataGoKrFacilityClient.PAGE_SIZE + 2)
        server.verify()
    }

    @Test
    fun `정상 - items가 item 래퍼 형태여도 수용하고, 좌표 불량 행은 건너뛴다(비상벨 필드 매핑)`() {
        val (client, server) = bind()
        val body = """
            {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
              {"lat":"37.51","lon":"127.02","mng_inst":"강남구청","rn_adres":"강남대로 1","sgg_cd":"11680"},
              {"lat":"","lon":"127.02","mng_inst":"좌표없음"},
              {"lat":"not-a-number","lon":"127.02","mng_inst":"좌표불량"}
            ]},"totalCount":3}}}
        """.trimIndent()
        server.expect(requestTo(containsString(DataGoKrFacilityClient.BELL_ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points = client.fetchEmergencyBells()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("강남구청")
        assertThat(points[0].sigungu).isEqualTo("11680")
    }

    @Test
    fun `정상 - 여성안심지킴이집 표준 필드(storNm·latitude·rdnmadr) 매핑`() {
        val (client, server) = bind()
        val body = envelope(
            """{"storNm":"GS25 강남점","latitude":"37.498","longitude":"127.027","rdnmadr":"강남대로 396"}""",
            1,
        )
        server.expect(requestTo(containsString(DataGoKrFacilityClient.SAFEHOUSE_ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points = client.fetchSafehouses()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("GS25 강남점")
        assertThat(points[0].lat).isEqualTo(37.498)
        assertThat(points[0].address).isEqualTo("강남대로 396")
    }

    @Test
    fun `정상 - 여성안심지킴이집 한글 컬럼 응답도 후보 폴백으로 매핑`() {
        val (client, server) = bind()
        val body = envelope(
            """{"업소명":"세븐일레븐 서초점","위도":"37.49","경도":"127.01","소재지도로명주소":"서초대로 11"}""",
            1,
        )
        server.expect(requestTo(containsString(DataGoKrFacilityClient.SAFEHOUSE_ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points = client.fetchSafehouses()

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
