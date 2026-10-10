package io.soo.bueongi.core.place

import io.soo.bueongi.client.tmap.TmapPlace
import io.soo.bueongi.client.tmap.TmapPlaceClient
import io.soo.bueongi.core.place.domain.error.PlaceSearchUnavailableException
import io.soo.bueongi.core.place.domain.service.PlaceSearchService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlaceSearchServiceTest {
    private val gangnam = TmapPlace("강남역 2번 출구", "서울 강남구 강남대로 396", 37.4979, 127.0276)

    @Test
    fun `외부 검색 결과를 응답으로 돌려준다`() {
        val client = mock<TmapPlaceClient>()
        whenever(client.search("강남")).thenReturn(listOf(gangnam))

        val result = PlaceSearchService(client).search("강남")

        assertThat(result.map { it.name }).containsExactly("강남역 2번 출구")
    }

    @Test
    fun `결과 항목은 좌표를 포함한다(경로 비교 입력용)`() {
        val client = mock<TmapPlaceClient>()
        whenever(client.search("강남역")).thenReturn(listOf(gangnam))

        val result = PlaceSearchService(client).search("강남역").first()

        assertThat(result.lat).isEqualTo(37.4979)
        assertThat(result.lng).isEqualTo(127.0276)
    }

    @Test
    fun `일치 항목이 없으면 빈 목록`() {
        val client = mock<TmapPlaceClient>()
        whenever(client.search("존재하지않는장소xyz")).thenReturn(emptyList())

        assertThat(PlaceSearchService(client).search("존재하지않는장소xyz")).isEmpty()
    }

    @Test
    fun `외부 검색이 실패하면 PlaceSearchUnavailableException`() {
        val client = mock<TmapPlaceClient>()
        whenever(client.search("강남")).thenThrow(RuntimeException("Kakao 5xx"))

        assertThatThrownBy { PlaceSearchService(client).search("강남") }
            .isInstanceOf(PlaceSearchUnavailableException::class.java)
    }
}
