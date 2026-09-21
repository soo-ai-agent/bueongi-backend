package io.soo.bueongi.client.datagokr

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class DataGoKrPagedFetcherHttpTest {

    private val endpoint: String = "https://apis.data.go.kr/1741000/test_resource/info"

    private val fields: DataGoKrFieldNames = DataGoKrFieldNames(
        lat = listOf("WGS84_LAT"),
        lng = listOf("WGS84_LOT"),
        name = listOf("MNG_INST_NM"),
        address = listOf("LCTN_ROAD_NM_ADDR"),
        sigungu = emptyList(),
    )

    private fun item(lat: Any = 37.5, lng: Any = 127.0, name: String = "관리기관") =
        """{"WGS84_LAT":"$lat","WGS84_LOT":"$lng","MNG_INST_NM":"$name","LCTN_ROAD_NM_ADDR":"서울시 도로명 1"}"""

    @Test
    fun `정상 - serviceKey 원형 유지 + 페이징 파라미터 + 필드 매핑`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("$endpoint?serviceKey=$TEST_SERVICE_KEY")))
            .andExpect(requestTo(containsString("pageNo=1&numOfRows=100&returnType=json&type=json")))
            .andRespond(withSuccess(envelope(item(), 1), MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = fetcher.fetchAll(endpoint, fields)

        assertThat(points).hasSize(1)
        assertThat(points!![0].lat).isEqualTo(37.5)
        assertThat(points[0].lng).isEqualTo(127.0)
        assertThat(points[0].name).isEqualTo("관리기관")
        assertThat(points[0].address).isEqualTo("서울시 도로명 1")
        server.verify()
    }

    @Test
    fun `정상 - properties 파일에서 포함된 인증키 따옴표를 제거한다`() {
        val (fetcher, server) = bindFetcher(key = "'$TEST_SERVICE_KEY'")
        server.expect(requestTo(containsString("serviceKey=$TEST_SERVICE_KEY")))
            .andRespond(withSuccess(envelope(item(), 1), MediaType.APPLICATION_JSON))

        assertThat(fetcher.fetchAll(endpoint, fields)).hasSize(1)
        server.verify()
    }

    @Test
    fun `정상 - 가득 찬 페이지는 다음 페이지로 순회하고 마지막 페이지에서 멈춘다`() {
        val (fetcher, server) = bindFetcher()
        val fullPage: String = (1..DataGoKrApiSpec.PAGE_SIZE).joinToString(",") { item(name = "기관$it") }
        val totalCount: Int = DataGoKrApiSpec.PAGE_SIZE + 2
        server.expect(requestTo(containsString("pageNo=1")))
            .andRespond(withSuccess(envelope(fullPage, totalCount), MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("pageNo=2")))
            .andRespond(
                withSuccess(
                    envelope("${item(name = "기관A")},${item(name = "기관B")}", totalCount),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = fetcher.fetchAll(endpoint, fields)

        assertThat(points).hasSize(DataGoKrApiSpec.PAGE_SIZE + 2)
        server.verify()
    }

    @Test
    fun `정상 - items가 item 래퍼 형태여도 수용하고 좌표 불량 행은 건너뛴다`() {
        val (fetcher, server) = bindFetcher()
        val body: String = """
            {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
              {"WGS84_LAT":"37.51","WGS84_LOT":"127.02","MNG_INST_NM":"강남구청","LCTN_ROAD_NM_ADDR":"강남대로 1"},
              {"WGS84_LAT":"","WGS84_LOT":"127.02","MNG_INST_NM":"좌표없음"},
              {"WGS84_LAT":"not-a-number","WGS84_LOT":"127.02","MNG_INST_NM":"좌표불량"}
            ]},"totalCount":3}}}
        """.trimIndent()
        server.expect(requestTo(containsString(endpoint)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = fetcher.fetchAll(endpoint, fields)

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("강남구청")
    }

    @Test
    fun `에러 - resultCode가 00이 아니면 null(기존 데이터 유지 신호)`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString(endpoint)))
            .andRespond(
                withSuccess(
                    """{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"},"body":{}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(fetcher.fetchAll(endpoint, fields)).isNull()
    }

    @Test
    fun `에러 - HTTP 5xx가 재시도까지 계속되면 null(부분수집으로 교체하지 않음)`() {
        val (fetcher, server) = bindFetcher()
        server.expect(
            ExpectedCount.times(DataGoKrApiSpec.MAX_ATTEMPTS_PER_PAGE),
            requestTo(containsString(endpoint)),
        ).andRespond(withServerError())

        assertThat(fetcher.fetchAll(endpoint, fields)).isNull()
        server.verify()
    }

    @Test
    fun `재시도 - 일시적 503 뒤 성공하면 그 페이지를 살려 수집을 이어간다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(ExpectedCount.once(), requestTo(containsString(endpoint)))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        server.expect(ExpectedCount.once(), requestTo(containsString(endpoint)))
            .andRespond(withSuccess(envelope(item(), 1), MediaType.APPLICATION_JSON))

        val points: List<DataGoKrPoint>? = fetcher.fetchAll(endpoint, fields)

        assertThat(points).hasSize(1)
        server.verify()
    }

    @Test
    fun `재시도 - 4xx는 영구 실패라 재시도하지 않고 즉시 중단한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(ExpectedCount.once(), requestTo(containsString(endpoint)))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThat(fetcher.fetchAll(endpoint, fields)).isNull()
        server.verify()
    }

    @Test
    fun `에러 - 키 미설정이면 isEnabled=false 이고 호출은 계약 위반이다`() {
        val (fetcher, _) = bindFetcher(key = " ")

        assertThat(fetcher.isEnabled).isFalse()
        assertThatThrownBy { fetcher.fetchAll(endpoint, fields) }.isInstanceOf(IllegalStateException::class.java)
    }
}
