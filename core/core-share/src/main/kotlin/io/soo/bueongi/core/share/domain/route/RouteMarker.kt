package io.soo.bueongi.core.share.domain.route

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    val x: Double? = null,
    val y: Double? = null,
    val name: String? = null,
    val purpose: String? = null,
    @JsonProperty("camera_count")
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)
