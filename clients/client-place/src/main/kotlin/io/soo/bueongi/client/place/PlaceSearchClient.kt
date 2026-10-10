package io.soo.bueongi.client.place

// 장소 공급자를 바꿀 때 core 는 그대로 두고 이 계약을 구현한 clients 모듈만 갈아 끼운다.
// 키가 없는 등 검색할 수 없으면 예외를 던진다 — 서비스가 그것을 503 으로 바꾼다.
interface PlaceSearchClient {
    fun search(keyword: String): List<Place>
}
