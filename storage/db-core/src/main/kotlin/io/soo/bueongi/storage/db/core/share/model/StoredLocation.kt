package io.soo.bueongi.storage.db.core.share.model

import java.time.Instant

/** 남은 거리·시간은 길안내 중에만 들어온다. */
data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
