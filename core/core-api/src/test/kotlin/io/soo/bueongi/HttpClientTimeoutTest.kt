package io.soo.bueongi

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.http.client.connect-timeout=300ms",
        "spring.http.client.read-timeout=300ms",
    ],
)
@ActiveProfiles("test")
class HttpClientTimeoutTest(
    @Autowired private val restClientBuilder: RestClient.Builder,
) {

    private val serverSleepMillis: Long = 3_000L

    private lateinit var slowServer: HttpServer

    @BeforeEach
    fun startSlowServer() {
        slowServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        slowServer.createContext("/slow") { exchange ->
            Thread.sleep(serverSleepMillis)
            val body = "late".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { stream -> stream.write(body) }
        }
        slowServer.start()
    }

    @AfterEach
    fun stopSlowServer() {
        slowServer.stop(0)
    }

    @Test
    fun `느린 응답은 read 타임아웃으로 끊긴다`() {
        val restClient: RestClient = restClientBuilder.clone().build()
        val slowUrl: String = "http://127.0.0.1:${slowServer.address.port}/slow"
        val startedAtMillis: Long = System.currentTimeMillis()

        assertThatThrownBy {
            restClient.get().uri(slowUrl).retrieve().body(String::class.java)
        }.isInstanceOf(ResourceAccessException::class.java)

        val elapsedMillis: Long = System.currentTimeMillis() - startedAtMillis
        assertThat(elapsedMillis).isLessThan(serverSleepMillis)
    }
}
