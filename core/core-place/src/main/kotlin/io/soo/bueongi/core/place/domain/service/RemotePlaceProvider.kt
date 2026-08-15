package io.soo.bueongi.core.place.domain.service

import io.soo.bueongi.core.place.domain.model.PlaceItem

/**
 * 외부 장소 검색 제공자. 키가 없으면 [isEnabled] 가 false 이고, 그때는 호출하지 않는다.
 * 구현을 갈아끼울 수 있게 인터페이스로 둔다 — 테스트는 꺼진 제공자를 대역으로 넣는다.
 */
interface RemotePlaceProvider {
    val isEnabled: Boolean
    fun search(keyword: String): List<PlaceItem>
}
