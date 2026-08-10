package io.soo.bueongi.client.kakao

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class KakaoPlaceClientHttpTest {

    private val apiKey: String = "test-rest-key"

    private fun bind(): Pair<KakaoPlaceClient, MockRestServiceServer> {
        val builder: RestClient.Builder = RestClient.builder()
        val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
        val provider: KakaoPlaceClient = KakaoPlaceClient(apiKey = apiKey, restClientBuilder = builder)
        return provider to server
    }

    @Test
    fun `성공 응답 - URI 쿼리·인증헤더 전송 + JSON 본문을 KakaoPlace 으로 매핑`() {
        val (provider, server) = bind()
        server.expect(requestTo(containsString("https://dapi.kakao.com/v2/local/search/keyword.json")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("query", "gangnam"))
            .andExpect(queryParam("size", "15"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "meta": { "total_count": 2, "is_end": true },
                      "documents": [
                        {
                          "place_name": "강남역 2번 출구",
                          "road_address_name": "서울 강남구 강남대로 396",
                          "address_name": "서울 강남구 역삼동 858",
                          "x": "127.0276",
                          "y": "37.4979",
                          "category_name": "교통,수송 > 지하철"
                        },
                        {
                          "place_name": "강남파출소",
                          "road_address_name": "",
                          "address_name": "서울 강남구 역삼동 800",
                          "x": "127.0250",
                          "y": "37.4990"
                        }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result: List<KakaoPlace> = provider.search("gangnam")

        server.verify()
        assertThat(result).containsExactly(
            KakaoPlace("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276),
            KakaoPlace("강남파출소", "서울 강남구 역삼동 800", 37.4990, 127.0250),
        )
    }

    @Test
    fun `성공 응답 - 좌표·필수필드 누락 문서는 결과에서 제외(HTTP 경유 mapNotNull)`() {
        val (provider, server) = bind()
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "documents": [
                        { "place_name": "정상", "road_address_name": "서울 어딘가 1", "x": "127.0", "y": "37.5" },
                        { "place_name": "좌표없음", "road_address_name": "서울 어딘가 2", "x": "abc", "y": "37.5" },
                        { "place_name": "주소없음", "x": "127.0", "y": "37.5" }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result: List<KakaoPlace> = provider.search("테스트")

        server.verify()
        assertThat(result).containsExactly(KakaoPlace("정상", "서울 어딘가 1", 37.5, 127.0))
    }

    @Test
    fun `빈 documents 응답이면 빈 목록`() {
        val (provider, server) = bind()
        server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
            .andRespond(withSuccess("""{ "documents": [] }""", MediaType.APPLICATION_JSON))

        assertThat(provider.search("없는장소")).isEmpty()
        server.verify()
    }

    @Test
    fun `5xx 응답이면 예외 전파(서비스가 시드로 폴백하도록)`() {
        val (provider, server) = bind()
        server.expect(ExpectedCount.once(), requestTo(containsString("/v2/local/search/keyword.json")))
            .andRespond(withServerError())

        assertThatThrownBy { provider.search("강남") }
            .isInstanceOf(RuntimeException::class.java)
        server.verify()
    }
}
