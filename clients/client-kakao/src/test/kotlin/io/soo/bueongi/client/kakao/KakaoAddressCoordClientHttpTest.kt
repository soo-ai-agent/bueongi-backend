package io.soo.bueongi.client.kakao

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.hamcrest.Matchers.containsString
import org.springframework.web.client.RestClient

class KakaoAddressCoordClientHttpTest {

    private fun bind(): Pair<KakaoAddressCoordClient, MockRestServiceServer> {
        val builder: RestClient.Builder = RestClient.builder()
        val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
        return KakaoAddressCoordClient(apiKey = "test-key", restClientBuilder = builder) to server
    }

    @Test
    fun `주소를 좌표로 바꾸고 같은 주소는 결과에 한 번만 담는다`() {
        val (client, server) = bind()
        val body: String = """{"documents":[{"x":"126.9432","y":"37.5445","address_name":"서울 마포구 토정로37길 46"}]}"""
        server.expect(requestTo(containsString("/v2/local/search/address.json")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val coords = client.geocodeAll(listOf("서울 마포구 토정로37길 46"))

        assertThat(coords).isNotNull()
        val point: KakaoAddressPoint = coords!!.getValue("서울 마포구 토정로37길 46")
        assertThat(point.lat).isEqualTo(37.5445)
        assertThat(point.lng).isEqualTo(126.9432)
        server.verify()
    }

    @Test
    fun `못 찾은 주소는 결과에서 빠지고 배치는 계속된다`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString("address.json")))
            .andRespond(withSuccess("""{"documents":[]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("address.json")))
            .andRespond(
                withSuccess("""{"documents":[{"x":"127.0276","y":"37.4979"}]}""", MediaType.APPLICATION_JSON),
            )

        val coords = client.geocodeAll(listOf("없는 주소 1-2-3", "서울 강남구 역삼동"))

        assertThat(coords).isNotNull()
        assertThat(coords!!).containsOnlyKeys("서울 강남구 역삼동")
    }

    @Test
    fun `통신이 실패하면 남은 주소를 돌지 않고 배치 전체를 실패로 돌린다`() {
        val (client, server) = bind()
        server.expect(ExpectedCount.once(), requestTo(containsString("address.json")))
            .andRespond(withServerError())

        val coords = client.geocodeAll(listOf("주소1", "주소2", "주소3"))

        assertThat(coords).isNull()
        server.verify()
    }

    @Test
    fun `속도 제한에 걸리면 쉬었다 같은 주소를 다시 시도한다`() {
        val (client, server) = bind()
        server.expect(requestTo(containsString("address.json")))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"errorType":"BadRequest","message":"API limit has been exceeded.","code":-10}"""),
            )
        server.expect(requestTo(containsString("address.json")))
            .andRespond(
                withSuccess("""{"documents":[{"x":"126.9432","y":"37.5445"}]}""", MediaType.APPLICATION_JSON),
            )

        val coords = client.geocodeAll(listOf("서울 마포구 토정로37길 46"))

        assertThat(coords).isNotNull()
        assertThat(coords!!).containsKey("서울 마포구 토정로37길 46")
        server.verify()
    }

    @Test
    fun `일일 상한을 넘는 배치는 호출 없이 실패로 돌린다`() {
        val (client, _) = bind()
        val tooMany: List<String> = (1..30_001).map { "주소$it" }

        assertThat(client.geocodeAll(tooMany)).isNull()
    }
}
