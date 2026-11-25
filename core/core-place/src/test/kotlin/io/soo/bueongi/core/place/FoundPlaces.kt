package io.soo.bueongi.core.place

import io.soo.bueongi.client.place.Place
import io.soo.bueongi.client.place.PlaceSearchClient

internal class FoundPlaces(
    private val places: List<Place>,
) : PlaceSearchClient {
    override fun search(keyword: String): List<Place> {
        return places
    }
}
