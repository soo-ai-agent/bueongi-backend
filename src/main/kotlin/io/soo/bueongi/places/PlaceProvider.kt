package io.soo.bueongi.places

/**
 * 장소 검색 소스 추상화. 검색어는 호출 전에 트림되어 비어있지 않음이 보장된다(빈 검색어 가드는 서비스 책임).
 * 구현체는 부분일치 의미만 책임지며, 응답은 항상 [PlaceItem] 계약을 따른다 — 시드/외부연동 어느 소스든 프론트는 동일 shape 를 받는다.
 */
interface PlaceProvider {
    fun search(keyword: String): List<PlaceItem>
}
