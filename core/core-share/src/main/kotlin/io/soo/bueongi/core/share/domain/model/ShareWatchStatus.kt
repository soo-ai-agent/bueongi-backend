package io.soo.bueongi.core.share.domain.model

import java.time.Instant

data class ShareWatchStatus(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
