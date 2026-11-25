package io.soo.bueongi.core.place

import io.soo.bueongi.client.place.Place
import io.soo.bueongi.client.place.PlaceSearchClient

internal class FailedSearch(
    private val cause: Exception,
) : PlaceSearchClient {
    override fun search(keyword: String): List<Place> {
        throw cause
    }
}
