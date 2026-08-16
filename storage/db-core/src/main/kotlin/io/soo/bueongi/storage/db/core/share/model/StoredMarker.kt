package io.soo.bueongi.storage.db.core.share.model

data class StoredMarker(
    val type: String,
    val lat: Double,
    val lng: Double,
    // 서버가 나중에 채우므로 받을 때는 null 이 가능함
    val x: Double?,
    // 서버가 나중에 채우므로 받을 때는 null 이 가능함
    val y: Double?,
    // 원천에 값이 없으면 null 이 가능함
    val name: String?,
    // CCTV 가 아닌 시설은 null 이 가능함
    val purpose: String?,
    // CCTV 가 아닌 시설은 null 이 가능함
    val cameraCount: Int?,
    // 지구대가 아닌 시설은 null 이 가능함
    val address: String?,
    // 지구대가 아닌 시설은 null 이 가능함
    val phone: String?,
)
