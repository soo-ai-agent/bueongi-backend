package io.soo.bueongi.storage.db.share.model

// 마커 종류마다 채워지는 칸이 달라, 앱이 보내지 않은 칸은 null 로 저장하고 응답에도 null 로 나간다.
data class StoredMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    val x: Double,
    val y: Double,
    val name: String?,
    val purpose: String?,
    val cameraCount: Int?,
    val address: String?,
    val phone: String?,
)
