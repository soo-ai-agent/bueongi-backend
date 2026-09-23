package io.soo.bueongi.core.place

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.client.kakao.KakaoPlace
import io.soo.bueongi.client.kakao.KakaoPlaceClient
import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import ch.qos.logback.classic.Logger as LogbackLogger

class PlaceSearchServiceSourceTest {
    @Test
    fun `검색어를 그대로 외부 클라이언트에 넘긴다`() {
        val client = mock<KakaoPlaceClient>()
        whenever(client.search("강남")).thenReturn(listOf(KakaoPlace("리모트장소", "서울 어딘가 1", 37.5, 127.0)))

        val result = PlaceSearchService(client, mock()).search("강남")

        verify(client).search("강남")
        assertThat(result.map { it.name }).containsExactly("리모트장소")
    }

    @Test
    fun `외부 검색이 실패하면 사용 불가를 알리고 원인을 잃지 않는다`() {
        val hidden = RuntimeException("Kakao 5xx")
        val client = mock<KakaoPlaceClient>()
        whenever(client.search("강남")).thenThrow(hidden)

        assertThatThrownBy { PlaceSearchService(client, mock()).search("강남") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)
            .hasRootCause(hidden)
    }

    @Test
    fun `실패 로그에 검색어를 남기지 않고 원인만 적는다`() {
        val logger: LogbackLogger = LoggerFactory.getLogger(PlaceSearchService::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val hidden = RuntimeException("Kakao 5xx", IllegalStateException("연결 끊김"))
        val client = mock<KakaoPlaceClient>()
        whenever(client.search("우리집 앞 골목")).thenThrow(hidden)

        assertThatThrownBy { PlaceSearchService(client, mock()).search("우리집 앞 골목") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)

        val line: String = appender.list.single().formattedMessage
        assertThat(line).doesNotContain("우리집")
        assertThat(line).startsWith("외부 장소 검색 실패")
        assertThat(line).contains("Kakao 5xx")
    }

    @Test
    fun `외부 검색이 실패하면 운영 이벤트를 발행한다`() {
        val client = mock<KakaoPlaceClient>()
        whenever(client.search("강남")).thenThrow(RuntimeException("Kakao 5xx"))
        val events = mock<ApplicationEventPublisher>()

        assertThatThrownBy { PlaceSearchService(client, events).search("강남") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)

        val captor = argumentCaptor<OperationEvent>()
        verify(events).publishEvent(captor.capture())
        assertThat(captor.firstValue.key).isEqualTo("kakao-place-failed")
        assertThat(captor.firstValue.detail).contains("Kakao 5xx")
    }
}
