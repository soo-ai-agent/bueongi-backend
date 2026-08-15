package io.soo.bueongi.core.domain.place

import io.soo.bueongi.core.support.error.InvalidKeywordException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PlaceSearchServiceSourceTest {

    private val seed: SeedPlaceProvider = SeedPlaceProvider()

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
        val service: PlaceSearchService = PlaceSearchService(seed, remote)

        val result = service.search("강남")

        assertThat(remote.calledWith).isNull()
        assertThat(result.map { it.name }).contains("강남역 2번 출구")
    }

    @Test
    fun `외부 키 설정 + 성공이면 실연동 결과를 반환(시드 아님)`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service: PlaceSearchService = PlaceSearchService(seed, remote)

        val result = service.search("강남")

        assertThat(remote.calledWith).isEqualTo("강남")
        assertThat(result).isEqualTo(remoteResult)
    }

    @Test
    fun `외부 검색어도 트림 후 전달`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service: PlaceSearchService = PlaceSearchService(seed, remote)

        service.search("  강남역  ")

        assertThat(remote.calledWith).isEqualTo("강남역")
    }

    @Test
    fun `외부 검색 실패(예외)면 시드로 폴백`() {
        val remote = FakeRemote(isEnabled = true) { throw RuntimeException("Kakao 5xx") }
        val service: PlaceSearchService = PlaceSearchService(seed, remote)

        val result = service.search("강남")

        assertThat(remote.calledWith).isEqualTo("강남")
        assertThat(result.map { it.name }).contains("강남역 2번 출구")
    }

    @Test
    fun `외부 키 설정이어도 빈 검색어는 외부 호출 없이 빈 목록`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service: PlaceSearchService = PlaceSearchService(seed, remote)

        assertThat(service.search("   ")).isEmpty()
        assertThat(remote.calledWith).isNull()
    }

    @Test
    fun `길이 초과 검색어는 외부 호출 전에 InvalidKeywordException - 외부 미호출`() {
        val remote = FakeRemote(isEnabled = true) { remoteResult }
        val service: PlaceSearchService = PlaceSearchService(seed, remote)

        val tooLong = "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH + 1)
        assertThatThrownBy { service.search(tooLong) }
            .isInstanceOf(InvalidKeywordException::class.java)
        assertThat(remote.calledWith).isNull()
    }
}
