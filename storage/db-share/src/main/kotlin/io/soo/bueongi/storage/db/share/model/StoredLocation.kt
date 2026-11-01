package io.soo.bueongi.storage.db.share.model

import java.time.Instant

data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    val remainingMeters: Double? = null,
    val remainingSeconds: Double? = null,
)
