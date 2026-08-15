package io.soo.bueongi.core.domain.place

interface PlaceProvider {
    fun search(keyword: String): List<PlaceItem>
}
