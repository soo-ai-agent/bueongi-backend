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

class SafemapPoliceClientHttpTest {

    private val serviceKey: String = "safemap%2Fkey"

    private fun bind(key: String = serviceKey): Pair<SafemapPoliceClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return SafemapPoliceClient(serviceKey = key, restClientBuilder = builder) to server
    }

    @Test
    fun `정상 - EPSG5179 x·y 를 WGS84 로 변환하고 관서명·주소·전화·시군구를 매핑한다`() {
        val (client, server) = bind()
        val (x, y) = Epsg5179.fromWgs84(lat = 37.4981, lng = 127.0276)
        val body: String = """
            {"resultCode":"00","resultMsg":"NORMAL","numOfRows":10,"pageNo":1,"totalCount":1,
             "items":[{"objt_id":"1","police":"서울청","polcsttn":"강남서","fclty_ty":"지구대","fclty_nm":"역삼지구대",
                       "adres":"서울 강남구 역삼동 1","rn_adres":"서울 강남구 테헤란로 1","telno":"02-123-4567",
                       "sgg_cd":"11680","x":"$x","y":"$y"}]}
        """.trimIndent()
        server.expect(requestTo(containsString("${SafemapApiSpec.ENDPOINT}?serviceKey=$serviceKey")))
            .andExpect(requestTo(containsString("returnType=JSON")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val facilities: List<SafemapPolicePoint>? = client.fetchPoliceFacilities()

        assertThat(facilities).hasSize(1)
        val f: SafemapPolicePoint = facilities!![0]
        assertThat(f.lat).isCloseTo(37.4981, within(1e-6))
        assertThat(f.lng).isCloseTo(127.0276, within(1e-6))
        assertThat(f.name).isEqualTo("역삼지구대")
        assertThat(f.address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(f.phone).isEqualTo("02-123-4567")
        assertThat(f.sigungu).isEqualTo("11680")
        server.verify()
    }

    @Test
    fun `정상 - 경위도(WGS84)로 오는 x·y 도 그대로 수용하고, 중첩 envelope·좌표불량 행을 처리한다`() {
        val (client, server) = bind()
        val body: String = """
            {"response":{"header":{"resultCode":"00"},"body":{"totalCount":3,"items":{"item":[
              {"fclty_nm":"WGS그대로","x":"127.0276","y":"37.4981"},
              {"fclty_nm":"순서뒤바뀜","x":"37.51","y":"127.02"},
              {"fclty_nm":"좌표불량","x":"999","y":"999"}
            ]}}}}
        """.trimIndent()
        server.expect(requestTo(containsString(SafemapApiSpec.ENDPOINT)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val facilities: List<SafemapPolicePoint>? = client.fetchPoliceFacilities()

        assertThat(facilities).hasSize(2)
        assertThat(facilities!![0].lat).isCloseTo(37.4981, within(1e-9))
        assertThat(facilities[0].lng).isCloseTo(127.0276, within(1e-9))
        assertThat(facilities[1].lat).isCloseTo(37.51, within(1e-9))
    }

    @Test
    fun `정상 - 서버가 요청보다 적게 돌려줘도 totalCount 까지 페이지를 순회한다`() {
        val (client, server) = bind()
        fun page(vararg names: String) =
            """{"resultCode":"00","totalCount":5,"items":[${
                names.joinToString(",") { """{"fclty_nm":"$it","x":"127.0","y":"37.5"}""" }
            }]}"""
        server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(page("a", "b"), MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("pageNo=2"))).andRespond(withSuccess(page("c", "d"), MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("pageNo=3"))).andRespond(withSuccess(page("e"), MediaType.APPLICATION_JSON))

        val facilities: List<SafemapPolicePoint>? = client.fetchPoliceFacilities()

        assertThat(facilities).hasSize(5)
        assertThat(facilities!!.map { it.name }).containsExactly("a", "b", "c", "d", "e")
        server.verify()
    }

    @Test
    fun `에러 - resultCode 가 0-00 이 아니면 null(기존 데이터 유지 신호)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString(SafemapApiSpec.ENDPOINT)))
            .andRespond(withSuccess("""{"resultCode":"30","resultMsg":"INVALID KEY"}""", MediaType.APPLICATION_JSON))

        assertThat(client.fetchPoliceFacilities()).isNull()
    }

    @Test
    fun `에러 - HTTP 5xx 면 null(부분수집으로 교체하지 않음)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString(SafemapApiSpec.ENDPOINT)))
            .andRespond(withServerError())

        assertThat(client.fetchPoliceFacilities()).isNull()
    }

    @Test
    fun `에러 - 키 미설정이면 isEnabled=false 이고 호출은 계약 위반이다`() {
        val (client, _) = bind(key = " ")

        assertThat(client.isEnabled).isFalse()
        assertThatThrownBy { client.fetchPoliceFacilities() }.isInstanceOf(IllegalStateException::class.java)
    }
}
