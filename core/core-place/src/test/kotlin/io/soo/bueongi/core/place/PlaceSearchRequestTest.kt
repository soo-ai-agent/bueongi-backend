package io.soo.bueongi.core.place

import io.soo.bueongi.core.place.api.request.PlaceSearchRequest
import io.soo.bueongi.core.place.domain.error.InvalidKeywordException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PlaceSearchRequestTest {
    @Test
    fun `앞뒤 공백은 트림한다`() {
        assertThat(PlaceSearchRequest("  강남  ").searchKeyword).isEqualTo("강남")
    }

    @Test
    fun `최대 길이 이하는 통과(경계값 포함)`() {
        val boundary = "가".repeat(PlaceSearchRequest.MAX_KEYWORD_LENGTH)
        assertThat(PlaceSearchRequest(boundary).searchKeyword).isEqualTo(boundary)
    }

    @Test
    fun `최대 길이를 초과하면 생성 시점에 InvalidKeywordException`() {
        val tooLong = "가".repeat(PlaceSearchRequest.MAX_KEYWORD_LENGTH + 1)
        assertThatThrownBy { PlaceSearchRequest(tooLong) }
            .isInstanceOf(InvalidKeywordException::class.java)
            .hasMessageContaining("${PlaceSearchRequest.MAX_KEYWORD_LENGTH + 1}자")
    }

    @Test
    fun `길이는 트림 후 기준 - 공백 패딩은 길이에 포함되지 않음`() {
        val padded = "  " + "가".repeat(PlaceSearchRequest.MAX_KEYWORD_LENGTH) + "  "
        assertThat(PlaceSearchRequest(padded).searchKeyword).hasSize(PlaceSearchRequest.MAX_KEYWORD_LENGTH)
    }
}
