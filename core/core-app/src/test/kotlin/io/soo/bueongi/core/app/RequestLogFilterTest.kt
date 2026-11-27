package io.soo.bueongi.core.app

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.core.common.config.RequestLogFilter
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

class RequestLogFilterTest {
    private fun chainSetting(status: Int) =
        FilterChain { _, response ->
            (response as HttpServletResponse).status = status
        }

    private fun chainWriting(status: Int, body: String, type: String) =
        FilterChain { _, response ->
            val http = response as HttpServletResponse
            http.status = status
            http.contentType = type
            http.writer.write(body)
            http.writer.flush()
        }

    @Test
    fun `관리자 조회는 관리자로 적는다`() {
        val appender = captureLogs()
        val filter = RequestLogFilter()

        filter.doFilter(
            MockHttpServletRequest("GET", "/api/admin/share-sessions/abc123/location"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[수신] 관리자")
        assertThat(line).doesNotContain("사용자")
    }

    @Test
    fun `관리자 인증 경로는 관리자로 적는다`() {
        val appender = captureLogs()
        val filter = RequestLogFilter()

        filter.doFilter(
            MockHttpServletRequest("POST", "/api/v1/auth/local/token/refresh"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        assertThat(appender.list.single().formattedMessage).startsWith("[수신] 관리자")
    }

    @Test
    fun `사용자 조회는 사용자로 적는다`() {
        val appender = captureLogs()
        val filter = RequestLogFilter()

        filter.doFilter(
            MockHttpServletRequest("GET", "/share/abc123/location"),
            MockHttpServletResponse(),
            chainSetting(200),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[수신] 사용자")
        assertThat(line).doesNotContain("관리자")
    }

    private fun captureLogs(): ListAppender<ILoggingEvent> {
        val logger: LogbackLogger = LoggerFactory.getLogger(RequestLogFilter::class.java) as LogbackLogger
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

        RequestLogFilter().doFilter(request, MockHttpServletResponse(), chainSetting(200))

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

        RequestLogFilter().doFilter(request, MockHttpServletResponse(), chainSetting(200))

        assertThat(appender.list.single().formattedMessage).contains("👤 anon-42")
    }

    @Test
    fun `헤더를 안 보내는 호출자는 unknown 으로 남긴다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("GET", "/api/places/search")

        RequestLogFilter().doFilter(request, MockHttpServletResponse(), chainSetting(200))

        assertThat(appender.list.single().formattedMessage).contains("👤 unknown")
    }

    @Test
    fun `서버 오류와 잘못된 요청은 warn 으로 올린다`() {
        val appender = captureLogs()

        RequestLogFilter()
            .doFilter(MockHttpServletRequest("GET", "/api/x"), MockHttpServletResponse(), chainSetting(500))
        RequestLogFilter()
            .doFilter(MockHttpServletRequest("GET", "/api/y"), MockHttpServletResponse(), chainSetting(404))
        RequestLogFilter()
            .doFilter(MockHttpServletRequest("GET", "/api/z"), MockHttpServletResponse(), chainSetting(200))

        assertThat(appender.list.map { it.level })
            .containsExactly(Level.WARN, Level.WARN, Level.INFO)
    }

    @Test
    fun `수신 요청은 방향을 맨 앞에 두고 상태 경로 순으로 적는다`() {
        val appender = captureLogs()
        val request = MockHttpServletRequest("GET", "/api/places/search")

        RequestLogFilter().doFilter(request, MockHttpServletResponse(), chainSetting(200))

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
        val filter = RequestLogFilter()

        filter.doFilter(
            MockHttpServletRequest("GET", "/api/admin/me"),
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
    fun `우리 API 밖 경로도 로그에 남긴다`() {
        val appender = captureLogs()
        val probe = MockHttpServletRequest("GET", "/wp-login.php")

        RequestLogFilter().doFilter(probe, MockHttpServletResponse(), chainSetting(404))

        assertThat(appender.list.single().formattedMessage).contains("404 GET", "/wp-login.php")
    }

    @Test
    fun `요청마다 짝 표식을 심고 끝나면 지운다`() {
        val appender = captureLogs()

        RequestLogFilter().doFilter(
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

        RequestLogFilter().doFilter(
            MockHttpServletRequest("POST", "/share/abc123/location"),
            MockHttpServletResponse(),
            chain,
        )

        assertThat(seenOrigin).isEqualTo("POST /share/{token}/location")
        assertThat(seenOrigin).doesNotContain("abc123")
    }

    @Test
    fun `아주 긴 경로는 잘라서 남긴다`() {
        val appender = captureLogs()
        val long = "/api/" + "a".repeat(500)

        RequestLogFilter()
            .doFilter(MockHttpServletRequest("GET", long), MockHttpServletResponse(), chainSetting(404))

        assertThat(appender.list.single().formattedMessage).contains("…")
        assertThat(appender.list.single().formattedMessage.length).isLessThan(long.length)
    }
}
