package io.soo.bueongi.core.place

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.soo.bueongi.client.place.Place
import io.soo.bueongi.core.place.api.response.PlaceResponse
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger as LogbackLogger

class PlaceSearchServiceTest {
    @Test
    fun `외부 검색 결과를 응답으로 돌려준다`() {
        val place: Place = Place("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276)
        val service: PlaceSearchService = PlaceSearchService(FoundPlaces(listOf(place)))

        val result: List<PlaceResponse> = service.search("강남")

        assertThat(result).containsExactly(PlaceResponse("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276))
    }

    @Test
    fun `외부 검색이 실패하면 사용 불가를 알리고 원인을 잃지 않는다`() {
        val cause: RuntimeException = RuntimeException("5xx")
        val service: PlaceSearchService = PlaceSearchService(FailedSearch(cause))

        assertThatThrownBy { service.search("강남") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)
            .hasRootCause(cause)
    }

    @Test
    fun `실패 로그에 검색어도 예외 메시지도 남기지 않는다`() {
        val logger: LogbackLogger = LoggerFactory.getLogger(PlaceSearchService::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender()
        appender.start()
        logger.addAppender(appender)
        val service: PlaceSearchService = PlaceSearchService(FailedSearch(RuntimeException("5xx")))

        assertThatThrownBy { service.search("우리집 앞 골목") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)

        val line: String = appender.list.single().formattedMessage
        assertThat(line).doesNotContain("우리집").doesNotContain("5xx")
        assertThat(line).contains("원인=RuntimeException")
    }
}
