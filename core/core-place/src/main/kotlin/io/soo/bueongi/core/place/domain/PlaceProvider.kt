package io.soo.bueongi.core.place.domain

interface PlaceProvider {
    fun search(keyword: String): List<PlaceItem>
}
