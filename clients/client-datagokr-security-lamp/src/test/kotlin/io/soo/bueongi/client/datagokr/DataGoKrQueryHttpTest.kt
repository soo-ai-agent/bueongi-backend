package io.soo.bueongi.client.datagokr

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.LocalDateTime

class DataGoKrQueryHttpTest {
    private val endpoint: String = "https://apis.data.go.kr/1741000/test_resource/info"

    private val fields: DataGoKrFieldNames =
        DataGoKrFieldNames(
            lat = listOf("WGS84_LAT"),
            lng = listOf("WGS84_LOT"),
            name = listOf("MNG_INST_NM"),
            address = listOf("LCTN_ROAD_NM_ADDR"),
            sigungu = emptyList(),
        )

    private val emptyPage: String =
        """{"response":{"header":{"resultCode":"00"},"body":{"items":[],"totalCount":0}}}"""

    @Test
    fun `자치단체 코드를 주면 cond 조건으로 실린다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("cond%5BOPN_ATMY_GRP_CD::EQ%5D=11680")))
            .andRespond(withSuccess(emptyPage, MediaType.APPLICATION_JSON))

        fetcher.fetchAll(endpoint, fields, DataGoKrQuery(municipalityCode = "11680"))

        server.verify()
    }

    @Test
    fun `갱신시점을 주면 증분 조건으로 실린다`() {
        val (fetcher, server) = bindFetcher()
        val since = DataGoKrUpdatePoint.of(LocalDateTime.of(2026, 8, 1, 0, 0, 0))
        server.expect(requestTo(containsString("cond%5BDAT_UPDT_PNT::GTE%5D=20260801000000")))
            .andRespond(withSuccess(emptyPage, MediaType.APPLICATION_JSON))

        fetcher.fetchAll(endpoint, fields, DataGoKrQuery(updatedSince = since))

        server.verify()
    }

    @Test
    fun `조건을 안 주면 cond 파라미터가 붙지 않는다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(not(containsString("cond"))))
            .andRespond(withSuccess(emptyPage, MediaType.APPLICATION_JSON))

        fetcher.fetchAll(endpoint, fields)

        server.verify()
    }

    @Test
    fun `응답 타입 파라미터는 문서명과 기존명을 함께 보낸다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("returnType=json&type=json")))
            .andRespond(withSuccess(emptyPage, MediaType.APPLICATION_JSON))

        fetcher.fetchAll(endpoint, fields)

        server.verify()
    }

    @Test
    fun `갱신시점 형식이 어긋나면 만들 때 막는다`() {
        assertThatThrownBy { DataGoKrUpdatePoint("2026-08-01") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `갱신시점은 14자리 숫자로 만들어진다`() {
        val point = DataGoKrUpdatePoint.of(LocalDateTime.of(2026, 12, 31, 23, 59, 59))

        assertThat(point.value).isEqualTo("20261231235959")
    }
}
