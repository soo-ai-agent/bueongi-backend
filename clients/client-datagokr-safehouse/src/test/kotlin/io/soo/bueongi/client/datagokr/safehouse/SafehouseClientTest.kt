package io.soo.bueongi.client.datagokr.safehouse

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

class SafehouseClientTest {
    @Test
    fun `표준 영문 필드(storNm·latitude·rdnmadr)를 매핑한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("tn_pubr_public_female_safety_prtchouse_api")))
            .andRespond(
                withSuccess(
                    standardEnvelope(
                        """{"storNm":"GS25 강남점","latitude":"37.498","longitude":"127.027","rdnmadr":"강남대로 396"}""",
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = SafehouseClient(fetcher).fetch()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("GS25 강남점")
        assertThat(points[0].lat).isEqualTo(37.498)
        assertThat(points[0].address).isEqualTo("강남대로 396")
    }

    @Test
    fun `한글 컬럼 응답도 후보 폴백으로 매핑한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("tn_pubr_public_female_safety_prtchouse_api")))
            .andRespond(
                withSuccess(
                    envelope(
                        """{"업소명":"세븐일레븐 서초점","위도":"37.49","경도":"127.01","소재지도로명주소":"서초대로 11"}""",
                        1,
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = SafehouseClient(fetcher).fetch()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("세븐일레븐 서초점")
        assertThat(points[0].lng).isEqualTo(127.01)
        assertThat(points[0].address).isEqualTo("서초대로 11")
    }
}
