package io.soo.bueongi.storage.db.core.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotPoint(
    val type: SafetySpotType,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val sigungu: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
    val address: String? = null,
    val phone: String? = null,
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)
}
