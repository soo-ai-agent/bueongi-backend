package io.soo.bueongi.core.domain.place

import io.soo.bueongi.client.kakao.KakaoPlaceClient
import org.springframework.stereotype.Component

@Component
class KakaoRemotePlaceProvider(
    private val client: KakaoPlaceClient,
) : RemotePlaceProvider {

    override val isEnabled: Boolean
        get() = client.isEnabled

    override fun search(keyword: String): List<PlaceItem> =
        client.search(keyword).map { PlaceItem(name = it.name, address = it.address, lat = it.lat, lng = it.lng) }
}
