package io.soo.bueongi.domain.place

import io.soo.bueongi.client.kakao.KakaoPlaceClient
import org.springframework.stereotype.Component

/**
 * [RemotePlaceProvider] 의 Kakao Local 구현 — 클라이언트 모듈([KakaoPlaceClient])을 도메인 계약(PlaceItem)으로
 * 감싸는 어댑터. 클라이언트가 경계 검증까지 끝낸 [io.soo.bueongi.client.kakao.KakaoPlace] 를 1:1 매핑만 한다.
 * (starter 규약: clients 모듈은 자체 모델을 반환하고, 도메인 매핑은 core-api 가 소유.)
 */
@Component
class KakaoRemotePlaceProvider(
    private val client: KakaoPlaceClient,
) : RemotePlaceProvider {

    override val isEnabled: Boolean
        get() = client.isEnabled

    override fun search(keyword: String): List<PlaceItem> =
        client.search(keyword).map { PlaceItem(name = it.name, address = it.address, lat = it.lat, lng = it.lng) }
}
