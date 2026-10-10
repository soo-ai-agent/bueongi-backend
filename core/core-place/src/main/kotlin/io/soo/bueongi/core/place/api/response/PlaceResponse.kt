package io.soo.bueongi.core.place.api.response

import io.soo.bueongi.client.place.Place

data class PlaceResponse(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun from(place: Place): PlaceResponse {
            return PlaceResponse(name = place.name, address = place.address, lat = place.lat, lng = place.lng)
        }

        fun from(places: List<Place>): List<PlaceResponse> {
            val responses: ArrayList<PlaceResponse> = ArrayList(places.size)
            for (place in places) {
                responses.add(from(place))
            }
            return responses
        }
    }
}
