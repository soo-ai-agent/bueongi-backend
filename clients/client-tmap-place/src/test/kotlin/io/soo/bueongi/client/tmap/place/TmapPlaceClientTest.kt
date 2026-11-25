package io.soo.bueongi.client.tmap.place

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient

class TmapPlaceClientTest {
    @Test
    fun `앱 키가 없으면 Tmap 을 부르지 않고 실패한다`() {
        val builder: RestClient.Builder = RestClient.builder()
        val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
        val client = TmapPlaceClient(appKey = " ", restClientBuilder = builder)

        assertThatThrownBy { client.search("강남역") }.isInstanceOf(IllegalStateException::class.java)

        server.verify()
    }
}
