package io.soo.bueongi.core.place

import io.soo.bueongi.core.place.domain.model.PlaceItem
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.place.domain.service.SeedPlaceProvider
import io.soo.bueongi.core.place.domain.error.InvalidKeywordException
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class PlaceSearchServiceSourceTest {

    private val seed = SeedPlaceProvider()

    private class FakeRemote(
        override val isEnabled: Boolean,
        private val behavior: (String) -> List<PlaceItem>,
    ) : RemotePlaceProvider {
        var calledWith: String? = null
        override fun search(keyword: String): List<PlaceItem> {
            calledWith = keyword
            return behavior(keyword)
        }
    }

    private val remoteResult = listOf(PlaceItem("리모트장소", "서울 어딘가 1", 37.5, 127.0))

    @Test
    fun `외부 키 미설정이면 외부 소스를 호출하지 않고 시드로 검색`() {
        val remote = FakeRemote(isEnabled = false) { error("호출되면 안 됨") }
        val service = PlaceSearchService(seed, remote)

        val result = service.search("강남")

        assertThat(remote.calledWith).isNull()
        assertThat(result.map { it.name }).contains("강남역 2번 출구")
    }

    @Test
    fun `외부 키 설정 + 성공이면 실연동 결과를 반환(시드 아님)`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service = PlaceSearchService(seed, remote)

        val result = service.search("강남")

        assertThat(remote.calledWith).isEqualTo("강남")
        assertThat(result).isEqualTo(remoteResult)
    }

    @Test
    fun `외부 검색어도 트림 후 전달`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service = PlaceSearchService(seed, remote)

        service.search("  강남역  ")

        assertThat(remote.calledWith).isEqualTo("강남역")
    }

    @Test
    fun `외부 검색 실패(예외)면 시드로 폴백`() {
        val remote = FakeRemote(isEnabled = true) { throw RuntimeException("Kakao 5xx") }
        val service = PlaceSearchService(seed, remote)

        val result = service.search("강남")

        assertThat(remote.calledWith).isEqualTo("강남")
        assertThat(result.map { it.name }).contains("강남역 2번 출구")
    }

    /** 사용자가 어디로 가려 했는지가 로그에 쌓이면 안 된다 — 실패해도 검색어는 남기지 않는다. */
    @Test
    fun `폴백 로그에 검색어를 남기지 않고 원인 사슬만 적는다`() {
        val logger: LogbackLogger = LoggerFactory.getLogger(PlaceSearchService::class.java) as LogbackLogger
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val hidden = RuntimeException("Kakao 5xx", IllegalStateException("연결 끊김"))
        val remote = FakeRemote(isEnabled = true) { throw hidden }

        PlaceSearchService(seed, remote).search("우리집 앞 골목")

        val line: String = appender.list.single().formattedMessage
        assertThat(line).doesNotContain("우리집")
        assertThat(line).startsWith("[예외] 무시")
        assertThat(line).contains("✖ RuntimeException: Kakao 5xx")
        assertThat(line).contains("IllegalStateException: 연결 끊김")
    }

    @Test
    fun `외부 키 설정이어도 빈 검색어는 외부 호출 없이 빈 목록`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service = PlaceSearchService(seed, remote)

        assertThat(service.search("   ")).isEmpty()
        assertThat(remote.calledWith).isNull()
    }

    @Test
    fun `길이 초과 검색어는 외부 호출 전에 InvalidKeywordException - 외부 미호출`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service = PlaceSearchService(seed, remote)

        val tooLong = "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH + 1)
        assertThatThrownBy { service.search(tooLong) }
            .isInstanceOf(InvalidKeywordException::class.java)
        assertThat(remote.calledWith).isNull()
    }
}
