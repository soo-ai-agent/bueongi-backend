package io.soo.bueongi.core.safetyspot.api.response

data class NearbySafetySpotResponse(
    val type: String,
    val lat: Double,
    val lng: Double,
    val name: String,
    val address: String,
    val phone: String,
)
