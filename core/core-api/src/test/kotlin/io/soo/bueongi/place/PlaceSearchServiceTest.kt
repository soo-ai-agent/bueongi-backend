package io.soo.bueongi.place

import io.soo.bueongi.support.error.InvalidKeywordException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PlaceSearchServiceTest {

    private val disabledRemote = object : RemotePlaceProvider {
        override val isEnabled: Boolean = false
        override fun search(keyword: String): List<PlaceItem> =
            throw AssertionError("disabled remote 는 호출되면 안 된다")
    }

    private val service = PlaceSearchService(SeedPlaceProvider(), disabledRemote)

    @Test
    fun `이름 부분일치로 검색`() {
        val result = service.search("강남")
        assertThat(result).isNotEmpty
        assertThat(result).allMatch { "강남" in it.name || "강남" in it.address }
        assertThat(result.map { it.name }).contains("강남역 2번 출구", "강남대학교")
    }

    @Test
    fun `주소 부분일치로 검색`() {
        val result = service.search("마포구")
        assertThat(result).isNotEmpty
        assertThat(result).allMatch { "마포구" in it.address }
    }

    @Test
    fun `빈 또는 공백 검색어는 빈 목록 - 추측 결과 없음`() {
        assertThat(service.search(null)).isEmpty()
        assertThat(service.search("")).isEmpty()
        assertThat(service.search("   ")).isEmpty()
    }

    @Test
    fun `앞뒤 공백은 트림 후 검색`() {
        assertThat(service.search("  강남  ")).isEqualTo(service.search("강남"))
    }

    @Test
    fun `일치 항목이 없으면 빈 목록`() {
        assertThat(service.search("존재하지않는장소xyz")).isEmpty()
    }

    @Test
    fun `최대 길이 이하 검색어는 정상 동작(경계값 포함)`() {
        val boundary = "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH)
        assertThat(service.search(boundary)).isEmpty()
    }

    @Test
    fun `최대 길이를 초과한 검색어는 InvalidKeywordException`() {
        val tooLong = "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH + 1)
        assertThatThrownBy { service.search(tooLong) }
            .isInstanceOf(InvalidKeywordException::class.java)
            .extracting("actualLength")
            .isEqualTo(PlaceSearchService.MAX_KEYWORD_LENGTH + 1)
    }

    @Test
    fun `길이는 트림 후 기준 - 공백 패딩은 길이에 포함되지 않음`() {
        val padded = "  " + "가".repeat(PlaceSearchService.MAX_KEYWORD_LENGTH) + "  "
        assertThat(service.search(padded)).isEmpty()
    }

    @Test
    fun `결과 항목은 좌표를 포함한다(경로 비교 입력용)`() {
        val gangnam = service.search("강남역").first()
        assertThat(gangnam.lat).isEqualTo(37.4979)
        assertThat(gangnam.lng).isEqualTo(127.0276)
    }
}
