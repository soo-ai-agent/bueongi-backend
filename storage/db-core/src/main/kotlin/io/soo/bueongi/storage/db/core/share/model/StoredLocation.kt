package io.soo.bueongi.storage.db.core.share.model

import java.time.Instant

data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
