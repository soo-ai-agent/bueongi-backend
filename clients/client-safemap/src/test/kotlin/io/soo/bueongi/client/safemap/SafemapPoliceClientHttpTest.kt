package io.soo.bueongi.client.safemap

import io.soo.bueongi.support.util.Epsg3857
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

    private val baseUrl: String = "https://safemap.go.kr"

    private fun bind(key: String = serviceKey): Pair<SafemapPoliceClient, MockRestServiceServer> {
        val builder: RestClient.Builder = RestClient.builder()
        val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
        val client = SafemapPoliceClient(serviceKey = key, baseUrl = baseUrl, restClientBuilder = builder)
        return client to server
    }

    @Test
    fun `정상 - EPSG3857 x·y 를 WGS84 로 변환하고 관서명·주소·전화·시군구를 매핑한다`() {
        val (client, server) = bind()
        val (x, y) = Epsg3857.fromWgs84(lat = 37.4981, lng = 127.0276)
        val body: String =
            """
            {"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"totalCount":1,"items":{"item":[
               {"objt_id":"1.0","fclty_ty":"지구대","fclty_nm":"역삼지구대",
                "rn_adres":"서울 강남구 테헤란로 1","telno":"02-000-0000","sgg_cd":"11680",
                "adres":"-","tmp_x":"0.0","tmp_y":"0.0","x":"$x","y":"$y"}]}}}
            """.trimIndent()
        server.expect(requestTo(containsString("$baseUrl/openapi2/IF_0036?serviceKey=$serviceKey")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val facilities: List<SafemapPolicePoint> = client.fetch().orEmpty()

        assertThat(facilities).hasSize(1)
        val police: SafemapPolicePoint = facilities.first()
        assertThat(police.lat).isCloseTo(37.4981, within(1e-6))
        assertThat(police.lng).isCloseTo(127.0276, within(1e-6))
        assertThat(police.name).isEqualTo("역삼지구대")
        assertThat(police.address).isEqualTo("서울 강남구 테헤란로 1")
        assertThat(police.phone).isEqualTo("02-000-0000")
        assertThat(police.sigungu).isEqualTo("11680")
        server.verify()
    }

    @Test
    fun `정상 - 좌표를 0 으로 채워 보낸 행은 버린다`() {
        val (client, server) = bind()
        val (x, y) = Epsg3857.fromWgs84(lat = 37.4981, lng = 127.0276)
        val body: String =
            """
            {"header":{"resultCode":"00"},
             "body":{"totalCount":2,"items":{"item":[
               {"fclty_nm":"좌표없음파출소","rn_adres":"전북 김제시 지평선로 1748","x":"0.0","y":"0.0"},
               {"fclty_nm":"역삼지구대","rn_adres":"서울 강남구 테헤란로 1","x":"$x","y":"$y"}]}}}
            """.trimIndent()
        server.expect(requestTo(containsString("/openapi2/IF_0036")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val facilities: List<SafemapPolicePoint> = client.fetch().orEmpty()

        assertThat(facilities).hasSize(1)
        assertThat(facilities.first().name).isEqualTo("역삼지구대")
    }

    @Test
    fun `정상 - 값 없음을 뜻하는 하이픈은 null 로 정규화한다`() {
        val (client, server) = bind()
        val (x, y) = Epsg3857.fromWgs84(lat = 37.4981, lng = 127.0276)
        val body: String =
            """
            {"header":{"resultCode":"00"},
             "body":{"totalCount":1,"items":{"item":[
               {"fclty_nm":"무인치안센터","rn_adres":"서울 강남구 테헤란로 1","telno":"-",
                "adres":"-","sgg_cd":"-","x":"$x","y":"$y"}]}}}
            """.trimIndent()
        server.expect(requestTo(containsString("/openapi2/IF_0036")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val police: SafemapPolicePoint = client.fetch().orEmpty().single()

        assertThat(police.phone).isNull()
        assertThat(police.sigungu).isNull()
        assertThat(police.name).isEqualTo("무인치안센터")
    }

    @Test
    fun `실패 - HTTP 오류면 null(기존 데이터 유지 신호)`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString("/openapi2/IF_0036")))
            .andRespond(withServerError())

        assertThat(client.fetch()).isNull()
    }

    @Test
    fun `실패 - resultCode 비정상이면 null`() {
        val (client, server) = bind()
        val body: String = """{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{}}"""
        server.expect(requestTo(containsString("/openapi2/IF_0036")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        assertThat(client.fetch()).isNull()
    }

    @Test
    fun `게이트 - 키 미설정이면 호출 전 차단(isEnabled=false)`() {
        val (client, _) = bind(key = "")

        assertThat(client.isEnabled).isFalse()
        assertThatThrownBy { client.fetch() }.isInstanceOf(IllegalStateException::class.java)
    }
}
