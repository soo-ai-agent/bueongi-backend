package io.soo.bueongi.api

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.storage.db.core.share.store.LocationStoreUnavailableException
import io.soo.bueongi.support.logging.beginTrace
import io.soo.bueongi.support.logging.currentTraceId
import io.soo.bueongi.support.logging.endTrace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ApiControllerAdviceLogTest {

    @AfterEach
    fun clearTrace() {
        endTrace()
    }

    private fun captureLogs(): ListAppender<ILoggingEvent> {
        val logger: LogbackLogger = LoggerFactory.getLogger(ApiControllerAdvice::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender
    }

    @Test
    fun `미처리 예외는 예외 줄로 남기고 원인 사슬을 끝까지 적는다`() {
        val appender = captureLogs()
        val root = IllegalArgumentException("Illegal unquoted character")
        val outer = IllegalStateException("Error while extracting response", root)

        ApiControllerAdvice().handleUncaught(outer)

        val event: ILoggingEvent = appender.list.single()
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.formattedMessage).startsWith("[예외] 미처리")
        assertThat(event.formattedMessage).contains("✖ IllegalStateException: Error while extracting response")
        assertThat(event.formattedMessage).contains("IllegalArgumentException: Illegal unquoted character")
        assertThat(event.formattedMessage).contains("↳ 500 INTERNAL_ERROR 응답")
    }

    @Test
    fun `미처리 예외는 스택도 함께 남긴다`() {
        val appender = captureLogs()

        ApiControllerAdvice().handleUncaught(IllegalStateException("터졌다"))

        assertThat(appender.list.single().throwableProxy).isNotNull()
    }

    @Test
    fun `저장소 장애도 같은 예외 줄로 남긴다`() {
        val appender = captureLogs()

        ApiControllerAdvice().handleShareStoreUnavailable(
            LocationStoreUnavailableException("findByToken", RuntimeException("redis down")),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("[예외] 미처리")
        assertThat(line).contains("공유 저장소")
        assertThat(line).contains("RuntimeException: redis down")
    }

    @Test
    fun `어느 요청에서 터졌는지 짝 표식과 함께 적는다`() {
        val appender = captureLogs()
        beginTrace("POST /share/{token}/location")

        ApiControllerAdvice().handleUncaught(IllegalStateException("터졌다"))

        val line: String = appender.list.single().formattedMessage
        assertThat(line).contains("POST /share/{token}/location")
        assertThat(line).contains("🔗 ${currentTraceId()}")
    }
}
