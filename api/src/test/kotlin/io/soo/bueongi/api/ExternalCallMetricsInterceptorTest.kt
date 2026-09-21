package io.soo.bueongi.api

import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.api.config.ExternalCallMetricsInterceptor
import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetric
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import io.soo.bueongi.support.logging.beginTrace
import io.soo.bueongi.support.logging.currentTraceId
import io.soo.bueongi.support.logging.endTrace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.io.IOException
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ExternalCallMetricsInterceptorTest {

    private class CapturingStore : ExternalCallMetricStore {
        val recorded: MutableList<ExternalCallMetric> = mutableListOf()
        override fun record(metric: ExternalCallMetric) {
            recorded.add(metric)
        }
        override fun findRecent(limit: Int): List<ExternalCallMetric> = recorded
    }

    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-22T03:00:00Z"), ZoneOffset.UTC)

    private fun requestTo(url: String, method: HttpMethod = HttpMethod.GET): MockClientHttpRequest =
        MockClientHttpRequest(method, URI.create(url))

    private fun executionReturning(status: HttpStatus): ClientHttpRequestExecution =
        ClientHttpRequestExecution { _, _ -> MockClientHttpResponse(ByteArray(0), status) }

    private fun captureLogs(): ListAppender<ILoggingEvent> {
        val logger: LogbackLogger =
            LoggerFactory.getLogger(ExternalCallMetricsInterceptor::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender
    }

    @AfterEach
    fun clearTrace() {
        endTrace()
    }

    @Test
    fun `외부 호출을 원천별로 기록한다`() {
        val store = CapturingStore()
        val interceptor = ExternalCallMetricsInterceptor(store, clock)

        interceptor.intercept(
            requestTo("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        val metric: ExternalCallMetric = store.recorded.single()
        assertThat(metric.provider).isEqualTo(ExternalApiProvider.TMAP)
        assertThat(metric.endpoint).isEqualTo("/tmap/routes/pedestrian")
        assertThat(metric.status).isEqualTo(200)
        assertThat(metric.failureReason).isNull()
    }

    @Test
    fun `질의 문자열은 기록하지도 남기지도 않는다`() {
        val appender = captureLogs()
        val store = CapturingStore()
        val interceptor = ExternalCallMetricsInterceptor(store, clock)

        interceptor.intercept(
            requestTo("https://apis.data.go.kr/1741000/x?serviceKey=SECRET-KEY-VALUE&pageNo=3"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).doesNotContain("SECRET-KEY-VALUE")
        assertThat(line).doesNotContain("serviceKey")
        assertThat(store.recorded.single().endpoint).doesNotContain("SECRET-KEY-VALUE")
    }

    @Test
    fun `원천마다 다른 이름으로 갈린다`() {
        val store = CapturingStore()
        val interceptor = ExternalCallMetricsInterceptor(store, clock)
        val hosts: Map<String, ExternalApiProvider> = mapOf(
            "https://dapi.kakao.com/v2/local/search/keyword.json" to ExternalApiProvider.KAKAO,
            "https://www.safetydata.go.kr/V2/api/DSSP-IF-00084" to ExternalApiProvider.SAFETYDATA,
            "https://www.safemap.go.kr/openApiService/data/getPoliceData.do" to ExternalApiProvider.SAFEMAP,
            "https://www.safe182.go.kr/api/lcm/openApi.do" to ExternalApiProvider.SAFE182,
            "https://hooks.slack.com/services/T000/B000/xxx" to ExternalApiProvider.SLACK,
            "https://apis.data.go.kr/1741000/y" to ExternalApiProvider.DATA_GO_KR,
            "https://example.invalid/whatever" to ExternalApiProvider.UNKNOWN,
        )

        for (url in hosts.keys) {
            interceptor.intercept(requestTo(url), ByteArray(0), executionReturning(HttpStatus.OK))
        }

        assertThat(store.recorded.map { it.provider }).isEqualTo(hosts.values.toList())
    }

    @Test
    fun `연결 실패는 상태 0 과 사유로 남기고 예외는 그대로 올린다`() {
        val appender = captureLogs()
        val store = CapturingStore()
        val interceptor = ExternalCallMetricsInterceptor(store, clock)
        val failing = ClientHttpRequestExecution { _, _ -> throw IOException("connect timed out") }

        assertThrows<IOException> {
            interceptor.intercept(requestTo("https://apis.openapi.sk.com/tmap/x"), ByteArray(0), failing)
        }

        val metric: ExternalCallMetric = store.recorded.single()
        assertThat(metric.status).isEqualTo(0)
        assertThat(metric.failureReason).isEqualTo("IOException")
        assertThat(appender.list.single().formattedMessage).contains("--- GET", "✖ IOException")
    }

    @Test
    fun `외부 호출은 방향을 맨 앞에 두고 원천 상태 경로 순으로 적는다`() {
        val appender = captureLogs()
        val interceptor = ExternalCallMetricsInterceptor(CapturingStore(), clock)

        interceptor.intercept(
            requestTo("https://dapi.kakao.com/v2/local/search/keyword.json"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[송신]")
        assertThat(line).doesNotContain("[수신]")
        val columns: List<String> = line.split("  |  ").map { it.trim() }
        assertThat(columns[1]).isEqualTo("kakao")
        assertThat(columns[2]).isEqualTo("200 GET")
        assertThat(columns[3]).isEqualTo("/v2/local/search/keyword.json")
        assertThat(columns[4]).endsWith("ms")
    }

    @Test
    fun `실패한 호출은 warn 으로 올린다`() {
        val appender = captureLogs()
        val interceptor = ExternalCallMetricsInterceptor(CapturingStore(), clock)

        interceptor.intercept(
            requestTo("https://dapi.kakao.com/v2/local/search/keyword.json"),
            ByteArray(0), executionReturning(HttpStatus.TOO_MANY_REQUESTS),
        )

        assertThat(appender.list.single().level.toString()).isEqualTo("WARN")
        assertThat(appender.list.single().formattedMessage).contains("429 GET")
    }

    @Test
    fun `왜 불렀는지를 남긴다 - 수신 요청에서 갈라졌으면 그 요청을 배치면 배치라고 적는다`() {
        val appender = captureLogs()
        val interceptor = ExternalCallMetricsInterceptor(CapturingStore(), clock)

        interceptor.intercept(
            requestTo("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )
        beginTrace("POST /api/routes")
        interceptor.intercept(
            requestTo("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        assertThat(appender.list[0].formattedMessage).contains("↩ 배치")
        assertThat(appender.list[0].formattedMessage).doesNotContain("🔗")
        assertThat(appender.list[1].formattedMessage).contains("↩ POST /api/routes")
        assertThat(appender.list[1].formattedMessage).contains("🔗 ${currentTraceId()}")
    }

    @Test
    fun `페이징 항목만 남기고 나머지 질의 문자열은 버린다`() {
        val appender = captureLogs()
        val interceptor = ExternalCallMetricsInterceptor(CapturingStore(), clock)

        interceptor.intercept(
            requestTo("https://apis.data.go.kr/1741000/x?serviceKey=SECRET-KEY-VALUE&pageNo=3&numOfRows=1000"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).contains("📄 pageNo=3 numOfRows=1000")
        assertThat(line).doesNotContain("SECRET-KEY-VALUE")
        assertThat(line).doesNotContain("serviceKey")
    }

    @Test
    fun `페이징 이름이라도 값이 숫자가 아니면 버린다`() {
        val appender = captureLogs()
        val interceptor = ExternalCallMetricsInterceptor(CapturingStore(), clock)

        interceptor.intercept(
            requestTo("https://apis.data.go.kr/1741000/x?pageNo=SECRET-KEY-VALUE"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).doesNotContain("SECRET-KEY-VALUE")
        assertThat(line).doesNotContain("📄")
    }

    @Test
    fun `실패 사유는 원인 사슬로 적되 URL 의 질의 문자열은 지운다`() {
        val appender = captureLogs()
        val hidden = IOException(
            """connect timed out: https://apis.data.go.kr/1741000/x?serviceKey=SECRET-KEY-VALUE""",
            IllegalStateException("소켓이 닫혔다"),
        )
        val interceptor = ExternalCallMetricsInterceptor(CapturingStore(), clock)

        assertThrows<IOException> {
            interceptor.intercept(
                requestTo("https://apis.data.go.kr/1741000/x"),
                ByteArray(0), ClientHttpRequestExecution { _, _ -> throw hidden },
            )
        }

        val line: String = appender.list.single().formattedMessage
        assertThat(line).contains("✖ IOException: connect timed out")
        assertThat(line).contains("IllegalStateException: 소켓이 닫혔다")
        assertThat(line).doesNotContain("SECRET-KEY-VALUE")
    }

    @Test
    fun `계측 저장 실패는 예외 줄로 남긴다`() {
        val appender = captureLogs()
        val brokenStore = object : ExternalCallMetricStore {
            override fun record(metric: ExternalCallMetric) = throw IllegalStateException("저장 불가")
            override fun findRecent(limit: Int): List<ExternalCallMetric> = emptyList()
        }

        ExternalCallMetricsInterceptor(brokenStore, clock).intercept(
            requestTo("https://apis.openapi.sk.com/tmap/x"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        val exceptionLine: String = appender.list.last().formattedMessage
        assertThat(exceptionLine).startsWith("[예외] 무시")
        assertThat(exceptionLine).contains("✖ IllegalStateException: 저장 불가")
    }

    @Test
    fun `계측 저장이 실패해도 외부 호출 결과는 그대로 돌려준다`() {
        val brokenStore = object : ExternalCallMetricStore {
            override fun record(metric: ExternalCallMetric) = throw IllegalStateException("저장 불가")
            override fun findRecent(limit: Int): List<ExternalCallMetric> = emptyList()
        }
        val interceptor = ExternalCallMetricsInterceptor(brokenStore, clock)

        val response: ClientHttpResponse = interceptor.intercept(
            requestTo("https://apis.openapi.sk.com/tmap/x"),
            ByteArray(0), executionReturning(HttpStatus.OK),
        )

        assertThat(response.statusCode.value()).isEqualTo(200)
    }
}
