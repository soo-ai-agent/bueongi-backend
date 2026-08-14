package io.soo.bueongi.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class FacilityPoi(
    val type: FacilityPoiType,
    val x: Int,
    val y: Int,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
)
