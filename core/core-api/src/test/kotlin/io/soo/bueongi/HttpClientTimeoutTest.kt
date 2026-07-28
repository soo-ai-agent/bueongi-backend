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

/**
 * 자동구성 `RestClient.Builder` 에 read 타임아웃이 실제로 적용되는지 고정한다.
 * 외부 클라이언트 4종(Tmap·Kakao·datagokr·safemap)이 모두 이 빌더를 `clone().build()` 해 쓰므로,
 * 이 테스트가 통과하면 외부가 응답만 지연시키는 "슬로우 장애"에서 호출이 무한 대기하지 않고 끊긴다
 * (= 워커 스레드 고갈로 전체 장애가 번지는 것을 막는다).
 *
 * 검증을 빠르게 하려고 프로덕션 기본값(application.yml: read 10s)을 테스트에서 300ms 로 낮춘다.
 */
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

    // 요청을 받으면 read 타임아웃보다 훨씬 오래 잠들었다가 응답하는 느린 테스트 서버.
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
        // 클라이언트 모듈과 동일하게 주입된 빌더를 clone().build() 해 사용한다.
        val restClient: RestClient = restClientBuilder.clone().build()
        val slowUrl: String = "http://127.0.0.1:${slowServer.address.port}/slow"
        val startedAtMillis: Long = System.currentTimeMillis()

        assertThatThrownBy {
            restClient.get().uri(slowUrl).retrieve().body(String::class.java)
        }.isInstanceOf(ResourceAccessException::class.java)

        // 서버 지연(3초)까지 기다린 게 아니라 타임아웃(300ms)으로 끊겼음을 소요 시간으로도 확인한다.
        val elapsedMillis: Long = System.currentTimeMillis() - startedAtMillis
        assertThat(elapsedMillis).isLessThan(serverSleepMillis)
    }
}
