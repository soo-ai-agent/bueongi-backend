package io.soo.bueongi.place

interface PlaceProvider {
    fun search(keyword: String): List<PlaceItem>
}
