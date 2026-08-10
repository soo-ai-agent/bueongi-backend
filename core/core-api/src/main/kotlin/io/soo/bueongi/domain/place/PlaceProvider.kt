package io.soo.bueongi.domain.place

interface PlaceProvider {
    fun search(keyword: String): List<PlaceItem>
}
