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

/**
 * [KakaoPlaceClient] 의 **실 HTTP 계층** 통합 테스트.
 *
 * 기존 [KakaoPlaceProviderTest] 는 isEnabled 게이트와 DTO 매핑만(네트워크 비의존) 고정한다. 여기서는
 * [MockRestServiceServer] 를 [RestClient.Builder] 에 바인딩해 실제 요청/응답 라운드트립을 e2e 로 고정한다:
 * 요청 URI/쿼리(query·size)·`KakaoAK` 인증 헤더·HTTP 메서드, 그리고 실제 Kakao JSON 응답이
 * 앱 Jackson 컨버터를 거쳐 [KakaoPlace] 으로 역직렬화·필터링되는 경로, 5xx 예외 전파(서비스 시드 폴백 트리거)까지.
 *
 * 한글 검색어는 와이어에서 percent-encoding 되므로 URI 문자열 비교 대신 [queryParam] 디코딩 매처로 검증한다.
 */
class KakaoPlaceClientHttpTest {

    private val apiKey: String = "test-rest-key"

    /** mock 서버를 바인딩한 builder 로 provider 를 만든다. provider 가 builder 를 clone 해도 mock 요청 팩토리는 보존된다. */
    private fun bind(): Pair<KakaoPlaceClient, MockRestServiceServer> {
        val builder: RestClient.Builder = RestClient.builder()
        val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
        val provider: KakaoPlaceClient = KakaoPlaceClient(apiKey = apiKey, restClientBuilder = builder)
        return provider to server
    }

    @Test
    fun `성공 응답 - URI 쿼리·인증헤더 전송 + JSON 본문을 KakaoPlace 으로 매핑`() {
        val (provider, server) = bind()
        // 검색어는 ASCII("gangnam") 로 검증한다 — queryParam 매처는 와이어의 raw(percent-encoded) 값을 비교하므로
        // 한글이면 인코딩 노이즈가 생긴다. 한글 응답 본문→KakaoPlace 매핑은 아래 별도 테스트가 커버.
        server.expect(requestTo(containsString("https://dapi.kakao.com/v2/local/search/keyword.json")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("query", "gangnam"))
            .andExpect(queryParam("size", "15"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey"))
            .andRespond(
                withSuccess(
                    // 실제 Kakao Local 응답을 단순화: meta(무시) + documents 2건.
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
            // 1건: 도로명주소 우선, x=lng / y=lat.
            KakaoPlace("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276),
            // 2건: 도로명주소 비어있으면 지번주소 폴백.
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
