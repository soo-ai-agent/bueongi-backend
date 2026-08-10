package io.soo.bueongi.storage.db.core.share

import java.time.Instant

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
