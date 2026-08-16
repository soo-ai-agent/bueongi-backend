package io.soo.bueongi.storage.db.core.share.model

import java.time.Instant

data class StoredLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: Instant,
    // 길안내 중이 아니면 null 이 가능함
    val remainingM: Double? = null,
    // 길안내 중이 아니면 null 이 가능함
    val remainingS: Double? = null,
)
