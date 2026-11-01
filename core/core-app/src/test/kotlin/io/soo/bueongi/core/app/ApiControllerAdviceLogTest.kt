package io.soo.bueongi.core.app

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.core.common.error.ApiControllerAdvice
import io.soo.bueongi.core.share.api.controller.ShareStoreAdvice
import io.soo.bueongi.storage.db.share.store.LocationStoreUnavailableException
import io.soo.bueongi.support.logging.beginTrace
import io.soo.bueongi.support.logging.currentTraceId
import io.soo.bueongi.support.logging.endTrace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import ch.qos.logback.classic.Logger as LogbackLogger

class ApiControllerAdviceLogTest {
    @AfterEach
    fun clearTrace() {
        endTrace()
    }

    private fun captureLogs(target: Class<*> = ApiControllerAdvice::class.java): ListAppender<ILoggingEvent> {
        val logger: LogbackLogger = LoggerFactory.getLogger(target) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        return appender
    }

    @Test
    fun `미처리 예외는 조치와 예외 종류를 남긴다`() {
        val appender = captureLogs()
        val root = IllegalArgumentException("Illegal unquoted character")
        val outer = IllegalStateException("Error while extracting response", root)

        advice().handleUncaught(outer)

        val event: ILoggingEvent = appender.list.single()
        assertThat(event.level).isEqualTo(Level.ERROR)
        assertThat(event.formattedMessage).startsWith("미처리 예외")
        assertThat(event.formattedMessage).contains("조치=500 INTERNAL_ERROR 응답")
        assertThat(event.formattedMessage).contains("원인=IllegalStateException")
    }

    @Test
    fun `예외 메시지와 스택은 로그에 남기지 않는다`() {
        val appender = captureLogs()
        val leaked = IllegalStateException("GET https://openapi.sk.com/route?appKey=LEAKED-1234")

        advice().handleUncaught(leaked)

        val event: ILoggingEvent = appender.list.single()
        assertThat(event.formattedMessage).doesNotContain("LEAKED-1234")
        assertThat(event.throwableProxy).isNull()
    }

    @Test
    fun `저장소 장애도 같은 형식으로 남긴다`() {
        val appender = captureLogs(ShareStoreAdvice::class.java)

        ShareStoreAdvice(ApplicationEventPublisher { }).handleShareStoreUnavailable(
            LocationStoreUnavailableException("findByToken", RuntimeException("redis down")),
        )

        val line: String = appender.list.single().formattedMessage
        assertThat(line).startsWith("미처리 예외")
        assertThat(line).contains("공유 저장소")
        assertThat(line).contains("원인=LocationStoreUnavailableException")
    }

    @Test
    fun `어느 요청에서 터졌는지 짝 표식과 함께 적는다`() {
        val appender = captureLogs()
        beginTrace("POST /share/{token}/location")

        advice().handleUncaught(IllegalStateException("터졌다"))

        val line: String = appender.list.single().formattedMessage
        assertThat(line).contains("POST /share/{token}/location")
        assertThat(line).contains("🔗 ${currentTraceId()}")
    }

    private fun advice(): ApiControllerAdvice = ApiControllerAdvice(ApplicationEventPublisher { })
}
