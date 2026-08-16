package io.soo.bueongi.storage.db.core.share.model

/** 부가 정보는 종류마다 있고 없다 — 카메라 대수는 CCTV, 전화는 지구대에만. */
data class StoredMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    val x: Double?,
    val y: Double?,
    val name: String?,
    val purpose: String?,
    val cameraCount: Int?,
    val address: String?,
    val phone: String?,
)
