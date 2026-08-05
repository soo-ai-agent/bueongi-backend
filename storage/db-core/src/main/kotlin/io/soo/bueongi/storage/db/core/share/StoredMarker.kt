package io.soo.bueongi.storage.db.core.share

import java.time.Instant

/** 경로변 안심시설 마커 1건(좌표 + 표시 힌트 + 정보 카드용 부가 필드). */
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
