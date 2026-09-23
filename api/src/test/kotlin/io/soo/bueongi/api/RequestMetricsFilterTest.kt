package io.soo.bueongi.api

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.api.config.RequestMetricsFilter
import io.soo.bueongi.core.enums.RequestCaller
import io.soo.bueongi.storage.db.core.requestmetric.AdminRequestMetricStore
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import io.soo.bueongi.support.logging.currentRequestOrigin
import io.soo.bueongi.support.logging.currentTraceId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import ch.qos.logback.classic.Logger as LogbackLogger

class RequestMetricsFilterTest {
    private class CapturingStore : RequestMetricStore {
        val recorded: MutableList<RequestMetric> = mutableListOf<RequestMetric>()

        override fun record(metric: RequestMetric) {
            recorded += metric
        }

        override fun findRecent(limit: Int): List<RequestMetric> = recorded.take(limit)
    }

    private class CapturingAdminStore : AdminRequestMetricStore {
        val recorded: MutableList<RequestMetric> = mutableListOf<RequestMetric>()

        override fun record(metric: RequestMetric) {
            recorded += metric
        }

        override fun findRecent(limit: Int): List<RequestMetric> = recorded.take(limit)
    }

    private fun filterOf(store: RequestMetricStore) = RequestMetricsFilter(store, CapturingAdminStore())

    private fun chainSetting(status: Int) =
        FilterChain { _, response ->
            (response as HttpServletResponse).status = status
        }

    private fun chainWriting(
        status: Int,
        body: String,
        type: String,
    ) = FilterChain { _, response ->
        val http = response as HttpServletResponse
        http.status = status
        http.contentType = type
        http.writer.write(body)
        http.writer.flush()
    }

    @Test
    fun `체인 실행 후 메서드 정규화경로 clientId status 를 기록한다`() {
        val store = CapturingStore()
        val request = MockHttpServletRequest("POST", "/share/abc123/location")
        request.addHeader("X-Client-Id", "anon-42")
        val response = MockHttpServletResponse()

        filterOf(store).doFilter(request, response, chainSetting(200))

        val metric: RequestMetric = store.recorded.single()
        assertThat(metric.method).isEqualTo("POST")
        assertThat(metric.endpoint).isEqualTo("/share/{token}/location")
        assertThat(metric.clientId).isEqualTo("anon-42")
        assertThat(metric.status).isEqualTo(200)
        assertThat(metric.durationMs).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `X-Client-Id 가 없거나 공백이면 clientId 는 null 이다`() {
        val store = CapturingStore()
        val missing = MockHttpServletRequest("GET", "/api/places/search")
        val blank = MockHttpServletRequest("GET", "/api/places/search").apply { addHeader("X-Client-Id", "   ") }

        filterOf(store).doFilter(missing, MockHttpServletResponse(), chainSetting(200))
        filterOf(store).doFilter(blank, MockHttpServletResponse(), chainSetting(200))

        assertThat(store.recorded.map { it.clientId }).containsExactly(null, null)
    }

    @Test
    fun `실패 status 도 그대로 기록한다`() {
        val store = CapturingStore()
        val request = MockHttpServletRequest("GET", "/api/places/search")
        request.addHeader("X-Client-Id", "anon-1")

        filterOf(store).doFilter(request, MockHttpServletResponse(), chainSetting(500))

        assertThat(store.recorded.single().status).isEqualTo(500)
    }

    @Test
    fun `관리자와 운영 경로는 계측하지 않는다`() {
        val store = CapturingStore()
        val filter = filterOf(store)
        val excluded: List<String> = listOf("/api/admin/traffic", "/actuator/health", "/healthz", "/cdn/safetySpots.json")

        excluded.forEach { path ->
            filter.doFilter(MockHttpServletRequest("GET", path), MockHttpServletResponse(), chainSetting(200))
        }

        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `관리자 조회는 로그에 남기되 사용자 트래픽과 갈라 적는다`() {
        val appender = captureLogs()
        val store = CapturingStore()
        val filter = filterOf(store)

        filter.doFilter(
            MockHttpServletRequest("GET", "/api/admin/share-sessions/abc123/location"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[수신] 관리자")
        assertThat(line).doesNotContain("사용자")
        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `관리자 인증 경로도 사용자 트래픽에서 뺀다`() {
        val store = CapturingStore()
        val filter = filterOf(store)
        val adminAuthPaths: List<String> =
            listOf(
                "/api/v1/auth/admin/login",
                "/api/v1/auth/local/token/refresh",
            )

        adminAuthPaths.forEach { path ->
            filter.doFilter(MockHttpServletRequest("POST", path), MockHttpServletResponse(), chainSetting(200))
        }

        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `관리자 호출은 관리자 창에만 담는다`() {
        val store = CapturingStore()
        val adminStore = CapturingAdminStore()
        val filter = RequestMetricsFilter(store, adminStore)

        filter.doFilter(
            MockHttpServletRequest("GET", "/api/admin/traffic"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        assertThat(store.recorded).isEmpty()
        val metric: RequestMetric = adminStore.recorded.single()
        assertThat(metric.endpoint).isEqualTo("/api/admin/traffic")
        assertThat(metric.caller).isEqualTo(RequestCaller.ADMIN)
    }

    @Test
    fun `사용자 호출은 사용자 창에만 담는다`() {
        val store = CapturingStore()
        val adminStore = CapturingAdminStore()
        val filter = RequestMetricsFilter(store, adminStore)

        filter.doFilter(
            MockHttpServletRequest("GET", "/api/places/search"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        assertThat(adminStore.recorded).isEmpty()
        assertThat(store.recorded.single().caller).isEqualTo(RequestCaller.USER)
    }

    @Test
    fun `우리 API 밖 경로는 관리자 창에도 안 담는다`() {
        val store = CapturingStore()
        val adminStore = CapturingAdminStore()

        RequestMetricsFilter(store, adminStore).doFilter(
            MockHttpServletRequest("GET", "/wp-login.php"),
            MockHttpServletResponse(),
            chainSetting(404),
        )

        assertThat(store.recorded).isEmpty()
        assertThat(adminStore.recorded).isEmpty()
    }

    @Test
    fun `관리자 인증 경로는 관리자로 적는다`() {
        val appender = captureLogs()
        val filter = filterOf(CapturingStore())

        filter.doFilter(
            MockHttpServletRequest("POST", "/api/v1/auth/local/token/refresh"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        assertThat(appender.list.single().formattedMessage).startsWith("[수신] 관리자")
    }

    @Test
    fun `사용자 조회는 사용자로 적고 통계에도 센다`() {
        val appender = captureLogs()
        val store = CapturingStore()
        val filter = filterOf(store)

        filter.doFilter(
            MockHttpServletRequest("GET", "/share/abc123/location"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[수신] 사용자")
        assertThat(line).doesNotContain("관리자")
        assertThat(store.recorded).hasSize(1)
    }

    private fun captureLogs(): ListAppender<ILoggingEvent> {
        val logger: LogbackLogger = LoggerFactory.getLogger(RequestMetricsFilter::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender
    }

    @Test
    fun `요청 한 줄을 남긴다 - 공유 토큰은 가리고 질의 문자열은 안 남긴다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("POST", "/share/abc123/location")
        request.queryString = "lat=37.5&lng=127.0"

        filterOf(CapturingStore()).doFilter(request, MockHttpServletResponse(), chainSetting(200))

        val line: String = appender.list.single().formattedMessage
        assertThat(line).contains("200 POST", "/share/{token}/location")
        assertThat(line).doesNotContain("abc123")
        assertThat(line).doesNotContain("37.5")
    }

    @Test
    fun `누가 불렀는지 익명 client-id 로 남긴다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("GET", "/api/places/search")
        request.addHeader("X-Client-Id", "anon-42")

        filterOf(CapturingStore()).doFilter(request, MockHttpServletResponse(), chainSetting(200))

        assertThat(appender.list.single().formattedMessage).contains("👤 anon-42")
    }

    @Test
    fun `헤더를 안 보내는 호출자는 unknown 으로 남긴다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("GET", "/api/places/search")

        filterOf(CapturingStore()).doFilter(request, MockHttpServletResponse(), chainSetting(200))

        assertThat(appender.list.single().formattedMessage).contains("👤 unknown")
    }

    @Test
    fun `서버 오류와 잘못된 요청은 warn 으로 올린다`() {
        val appender = captureLogs()

        filterOf(CapturingStore())
            .doFilter(MockHttpServletRequest("GET", "/api/x"), MockHttpServletResponse(), chainSetting(500))
        filterOf(CapturingStore())
            .doFilter(MockHttpServletRequest("GET", "/api/y"), MockHttpServletResponse(), chainSetting(404))
        filterOf(CapturingStore())
            .doFilter(MockHttpServletRequest("GET", "/api/z"), MockHttpServletResponse(), chainSetting(200))

        assertThat(appender.list.map { it.level })
            .containsExactly(Level.WARN, Level.WARN, Level.INFO)
    }

    @Test
    fun `수신 요청은 방향을 맨 앞에 두고 상태 경로 순으로 적는다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("GET", "/api/places/search")

        filterOf(CapturingStore()).doFilter(request, MockHttpServletResponse(), chainSetting(200))

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[수신] 사용자")
        assertThat(line).doesNotContain("[송신]")
        val columns: List<String> = line.split("  |  ").map { it.trim() }
        assertThat(columns[1]).isEqualTo("200 GET")
        assertThat(columns[2]).isEqualTo("/api/places/search")
        assertThat(columns[3]).endsWith("ms")
    }

    @Test
    fun `앞의 두 칸은 값이 짧아도 폭을 채운다`() {
        val appender = captureLogs()
        val filter = filterOf(CapturingStore())

        filter.doFilter(
            MockHttpServletRequest("GET", "/api/admin/traffic"),
            MockHttpServletResponse(),
            chainSetting(200),
        )
        filter.doFilter(
            MockHttpServletRequest("DELETE", "/api/x"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        val pathStarts: List<Int> = appender.list.map { it.formattedMessage.indexOf("/api/") }
        assertThat(pathStarts[0]).isEqualTo(pathStarts[1])
    }

    @Test
    fun `우리 API 밖 경로는 로그만 남기고 통계에는 안 센다`() {
        val appender = captureLogs()
        val store = CapturingStore()
        val probe = MockHttpServletRequest("GET", "/wp-login.php")

        filterOf(store).doFilter(probe, MockHttpServletResponse(), chainSetting(404))

        assertThat(appender.list.single().formattedMessage).contains("404 GET", "/wp-login.php")
        assertThat(store.recorded).isEmpty()
    }

    @Test
    fun `요청마다 짝 표식을 심고 끝나면 지운다`() {
        val appender = captureLogs()

        filterOf(CapturingStore()).doFilter(
            MockHttpServletRequest("GET", "/api/places/search"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        assertThat(appender.list.single().formattedMessage).containsPattern("🔗 [0-9a-f]{8}")
        assertThat(currentTraceId()).isEmpty()
    }

    @Test
    fun `왜 불렸는지는 토큰을 가린 경로로 남긴다`() {
        var seenOrigin: String = ""
        val chain =
            FilterChain { _, response ->
                seenOrigin = currentRequestOrigin()
                (response as HttpServletResponse).status = 200
            }

        filterOf(CapturingStore()).doFilter(
            MockHttpServletRequest("POST", "/share/abc123/location"),
            MockHttpServletResponse(),
            chain,
        )

        assertThat(seenOrigin).isEqualTo("POST /share/{token}/location")
        assertThat(seenOrigin).doesNotContain("abc123")
    }

    @Test
    fun `통계 기록 실패는 로그로 남기고 응답은 그대로 나간다`() {
        val appender = captureLogs()
        val broken =
            object : RequestMetricStore {
                override fun record(metric: RequestMetric) = throw IllegalStateException("창이 닫혔다", IllegalArgumentException("연결 없음"))

                override fun findRecent(limit: Int): List<RequestMetric> = emptyList()
            }
        val response = MockHttpServletResponse()

        RequestMetricsFilter(broken, CapturingAdminStore()).doFilter(
            MockHttpServletRequest("GET", "/api/places/search"),
            response,
            chainSetting(200),
        )

        val failureEvent = appender.list.last()
        assertThat(failureEvent.formattedMessage).startsWith("요청 계측 실패")
        assertThat(failureEvent.throwableProxy.message).isEqualTo("창이 닫혔다")
        assertThat(failureEvent.throwableProxy.cause.message).isEqualTo("연결 없음")
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `아주 긴 경로는 잘라서 남긴다`() {
        val appender = captureLogs()
        val long = "/api/" + "a".repeat(500)

        filterOf(CapturingStore())
            .doFilter(MockHttpServletRequest("GET", long), MockHttpServletResponse(), chainSetting(404))

        assertThat(appender.list.single().formattedMessage).contains("…")
        assertThat(appender.list.single().formattedMessage.length).isLessThan(long.length)
    }
}
