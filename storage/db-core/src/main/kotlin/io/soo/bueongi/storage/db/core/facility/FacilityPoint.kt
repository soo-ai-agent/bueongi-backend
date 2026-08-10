package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

data class FacilityPoint(
    val type: FacilityType,
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
