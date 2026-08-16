package io.soo.bueongi.storage.db.core.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType

data class SafetySpotPoint(
    val type: SafetySpotType,
    val lat: Double,
    val lng: Double,
    // 원천에 값이 없으면 null 이 가능함
    val name: String? = null,
    // 원천에 값이 없으면 null 이 가능함
    val sigungu: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val purpose: String? = null,
    // CCTV 가 아닌 시설은 null 이 가능함
    val cameraCount: Int? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val address: String? = null,
    // 지구대가 아닌 시설은 null 이 가능함
    val phone: String? = null,
) {
    fun toGeoPoint(): GeoPoint =
        GeoPoint(lat, lng)
}
