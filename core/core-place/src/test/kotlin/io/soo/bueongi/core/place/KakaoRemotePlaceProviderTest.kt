package io.soo.bueongi.core.place

import io.soo.bueongi.core.place.domain.service.KakaoRemotePlaceProvider
import io.soo.bueongi.core.place.domain.model.PlaceItem
import io.soo.bueongi.client.kakao.KakaoPlace
import io.soo.bueongi.client.kakao.KakaoPlaceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
