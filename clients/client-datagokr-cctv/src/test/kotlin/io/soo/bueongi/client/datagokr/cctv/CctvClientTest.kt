package io.soo.bueongi.client.datagokr.cctv

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.bindFetcher
import io.soo.bueongi.client.datagokr.envelope
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class CctvClientTest {

    @Test
    fun `전국 CCTV 필드(WGS84_LAT·MNG_INST_NM·LCTN_ROAD_NM_ADDR)를 매핑한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("/1741000/cctv_info/info")))
            .andRespond(
                withSuccess(
                    envelope(
                        """{"WGS84_LAT":"37.5","WGS84_LOT":"127.0","MNG_INST_NM":"강남구청","LCTN_ROAD_NM_ADDR":"강남대로 1"}""",
                        1,
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = CctvClient(fetcher).fetch()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("강남구청")
        assertThat(points[0].lat).isEqualTo(37.5)
        assertThat(points[0].address).isEqualTo("강남대로 1")
        server.verify()
    }
}
