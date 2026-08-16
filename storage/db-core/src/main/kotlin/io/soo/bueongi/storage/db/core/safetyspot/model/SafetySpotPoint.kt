package io.soo.bueongi.storage.db.core.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotType

/** 부가 정보는 종류마다 있고 없다 — 카메라 대수는 CCTV, 전화는 지구대에만. */
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
    fun toGeoPoint(): GeoPoint =
        GeoPoint(lat, lng)
}
