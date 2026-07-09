package io.soo.bueongi.client.kakao

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * [KakaoPlaceClient] 의 키 게이트([isEnabled]) 및 응답 매핑([KakaoPlaceDocument.toKakaoPlace]) 단위 테스트.
 * 실제 HTTP 호출은 하지 않는다(네트워크 비의존) — isEnabled 분기와 DTO→KakaoPlace 매핑 규칙만 고정.
 */
class KakaoPlaceClientTest {

    private fun provider(apiKey: String) =
        KakaoPlaceClient(apiKey = apiKey, restClientBuilder = RestClient.builder())

    @Test
    fun `키 미설정이면 isEnabled=false`() {
        assertThat(provider("").isEnabled).isFalse()
        assertThat(provider("   ").isEnabled).isFalse()
    }

    @Test
    fun `키 설정이면 isEnabled=true`() {
        assertThat(provider("test-key").isEnabled).isTrue()
    }

    @Test
    fun `isEnabled=false 상태로 search 호출하면 명확히 실패`() {
        assertThatThrownBy { provider("").search("강남") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `문서 매핑 - 도로명주소 우선`() {
        val doc = KakaoPlaceDocument(
            placeName = "강남역",
            roadAddressName = "서울 강남구 강남대로 396",
            addressName = "서울 강남구 역삼동 858",
            lng = "127.0276",
            lat = "37.4979",
        )
        assertThat(doc.toKakaoPlace())
            .isEqualTo(KakaoPlace("강남역", "서울 강남구 강남대로 396", 37.4979, 127.0276))
    }

    @Test
    fun `문서 매핑 - 도로명주소 없으면 지번주소로 폴백`() {
        val doc = KakaoPlaceDocument(
            placeName = "어떤장소",
            roadAddressName = "",
            addressName = "서울 강남구 역삼동 858",
            lng = "127.0",
            lat = "37.5",
        )
        assertThat(doc.toKakaoPlace()?.address).isEqualTo("서울 강남구 역삼동 858")
    }

    @Test
    fun `문서 매핑 - 이름이나 좌표 누락이면 제외(null)`() {
        assertThat(KakaoPlaceDocument(placeName = null, lng = "127.0", lat = "37.5").toKakaoPlace()).isNull()
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = null, lat = "37.5").toKakaoPlace(),
        ).isNull()
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "abc", lat = "37.5").toKakaoPlace(),
        ).isNull()
    }

    @Test
    fun `문서 매핑 - 비유한 좌표(NaN, Infinity)는 제외(null)`() {
        // "NaN".toDoubleOrNull()=NaN, "Infinity"=∞ 로 파싱은 통과하지만 좌표로는 무효 → 제외.
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "127.0", lat = "NaN").toKakaoPlace(),
        ).isNull()
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "Infinity", lat = "37.5").toKakaoPlace(),
        ).isNull()
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "127.0", lat = "-Infinity").toKakaoPlace(),
        ).isNull()
    }

    @Test
    fun `문서 매핑 - 지리 범위 밖 좌표는 제외(null)`() {
        // 파싱은 되지만 위도 -90~90 / 경도 -180~180 을 벗어난 좌표는 제외(downstream 가드 우회 방지).
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "127.0", lat = "91.0").toKakaoPlace(),
        ).isNull()
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "181.0", lat = "37.5").toKakaoPlace(),
        ).isNull()
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "127.0", lat = "-90.5").toKakaoPlace(),
        ).isNull()
    }

    @Test
    fun `문서 매핑 - 범위 경계 좌표(±90, ±180)는 유효`() {
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "180.0", lat = "90.0").toKakaoPlace(),
        ).isEqualTo(KakaoPlace("x", "주소", 90.0, 180.0))
        assertThat(
            KakaoPlaceDocument(placeName = "x", roadAddressName = "주소", lng = "-180.0", lat = "-90.0").toKakaoPlace(),
        ).isEqualTo(KakaoPlace("x", "주소", -90.0, -180.0))
    }
}
