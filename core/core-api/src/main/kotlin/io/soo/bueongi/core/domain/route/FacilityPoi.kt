package io.soo.bueongi.core.domain.route

import io.soo.bueongi.core.enums.FacilityPoiType

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
