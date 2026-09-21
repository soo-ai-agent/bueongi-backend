package io.soo.bueongi.client.datagokr.bell

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.bindFetcher
import io.soo.bueongi.client.datagokr.envelope
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class EmergencyBellClientTest {
    @Test
    fun `안전비상벨 필드(INSTL_PSTN·lon·sgg_cd)를 매핑한다`() {
        val (fetcher, server) = bindFetcher()
        server.expect(requestTo(containsString("/1741000/emergency_call_box_info/info")))
            .andRespond(
                withSuccess(
                    envelope(
                        """{"WGS84_LAT":"37.51","WGS84_LOT":"127.02","INSTL_PSTN":"신문초삼거리","rn_adres":"강남대로 1","sgg_cd":"11680"}""",
                        1,
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val points: List<DataGoKrPoint>? = EmergencyBellClient(fetcher).fetch()

        assertThat(points).hasSize(1)
        assertThat(points!![0].name).isEqualTo("신문초삼거리")
        assertThat(points[0].address).isEqualTo("강남대로 1")
        assertThat(points[0].sigungu).isEqualTo("11680")
        server.verify()
    }
}
