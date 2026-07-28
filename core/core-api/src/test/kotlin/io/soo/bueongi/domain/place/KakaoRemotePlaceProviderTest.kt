package io.soo.bueongi.domain.place

import io.soo.bueongi.client.kakao.KakaoPlace
import io.soo.bueongi.client.kakao.KakaoPlaceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [KakaoRemotePlaceProvider] — 클라이언트 모델(KakaoPlace) → 도메인 계약(PlaceItem) 매핑과
 * isEnabled 위임을 고정한다. 구 단일모듈 스위트는 JSON→PlaceItem 을 끝까지 단언했는데,
 * 모듈 분리로 이 마지막 구간(어댑터)이 무단언으로 남는 회귀를 막는다.
 */
class KakaoRemotePlaceProviderTest {

    @Test
    fun `KakaoPlace 를 PlaceItem 으로 1대1 매핑한다`() {
        val client = mock<KakaoPlaceClient>()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.search("강남")).thenReturn(
            listOf(KakaoPlace(name = "강남역 2번 출구", address = "서울 강남구 강남대로 396", lat = 37.4979, lng = 127.0276)),
        )
        val provider: KakaoRemotePlaceProvider = KakaoRemotePlaceProvider(client)

        assertThat(provider.search("강남")).containsExactly(
            PlaceItem(name = "강남역 2번 출구", address = "서울 강남구 강남대로 396", lat = 37.4979, lng = 127.0276),
        )
    }

    @Test
    fun `isEnabled 는 클라이언트 키 설정 여부를 그대로 위임한다`() {
        val disabled = mock<KakaoPlaceClient>()
        whenever(disabled.isEnabled).thenReturn(false)
        assertThat(KakaoRemotePlaceProvider(disabled).isEnabled).isFalse()

        val enabled = mock<KakaoPlaceClient>()
        whenever(enabled.isEnabled).thenReturn(true)
        assertThat(KakaoRemotePlaceProvider(enabled).isEnabled).isTrue()
    }
}
