package io.soo.bueongi.core.place.api.response

import io.soo.bueongi.client.tmap.TmapPlace

data class PlaceResponse(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
) {
    companion object {
        fun from(place: TmapPlace): PlaceResponse {
            return PlaceResponse(
                name = place.name,
                address = place.address,
                lat = place.lat,
                lng = place.lng,
            )
        }
    }
}
