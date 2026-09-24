package io.soo.bueongi.client.datagokr.securitylamp

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.bindFetcher
import io.soo.bueongi.client.datagokr.envelope
import io.soo.bueongi.client.datagokr.standardEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class SecurityLampClientTest {
    @Test
    fun `표준 영문 필드(lmpLcNm·latitude·insttCode)를 매핑한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("tn_pubr_public_scrty_lmp_api")))
            .andRespond(
                withSuccess(
                    standardEnvelope(
                        """
                        {"lmpLcNm":"역삼동 골목 보안등","latitude":"37.5008","longitude":"127.0359",
                        "rdnmadr":"테헤란로 1","insttCode":"3210000","institutionNm":"서울특별시 서초구청"}
                        """.trimIndent().replace("\n", ""),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = SecurityLampClient(fetcher).fetchSecurityLamps()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("역삼동 골목 보안등")
        assertThat(points[0].lat).isEqualTo(37.5008)
        assertThat(points[0].sigungu).isEqualTo("3210000")
    }

    @Test
    fun `한글 컬럼 응답도 후보 폴백으로 매핑한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("tn_pubr_public_scrty_lmp_api")))
            .andRespond(
                withSuccess(
                    envelope(
                        """{"보안등위치명":"대청동 보안등","위도":"35.1018","경도":"129.032","소재지지번주소":"부산 중구 대청동","시군구코드":"26110"}""",
                        1,
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = SecurityLampClient(fetcher).fetchSecurityLamps()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("대청동 보안등")
        assertThat(points[0].lng).isEqualTo(129.032)
        assertThat(points[0].sigungu).isEqualTo("26110")
    }

    @Test
    fun `위치명이 없어도 관리 부서명을 이름으로 쓰지 않는다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("tn_pubr_public_scrty_lmp_api")))
            .andRespond(
                withSuccess(
                    standardEnvelope(
                        """{"latitude":"37.5008","longitude":"127.0359","institutionNm":"경기도 부천시 오정구 건설안전과"}""",
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = SecurityLampClient(fetcher).fetchSecurityLamps()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isNull()
    }
}
