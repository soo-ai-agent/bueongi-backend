package io.soo.bueongi.client.place

interface PlaceSearchClient {
    fun search(keyword: String): List<Place>
}
