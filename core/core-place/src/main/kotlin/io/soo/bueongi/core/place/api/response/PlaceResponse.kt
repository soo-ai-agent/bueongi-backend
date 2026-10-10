package io.soo.bueongi.core.place.api.response

data class PlaceResponse(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)
