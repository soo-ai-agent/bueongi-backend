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
        val provider = KakaoRemotePlaceProvider(client)

        assertThat(provider.search("강남")).containsExactly(
            PlaceItem(name = "강남역 2번 출구", address = "서울 강남구 강남대로 396", lat = 37.4979, lng = 127.0276),
        )
    }
}
