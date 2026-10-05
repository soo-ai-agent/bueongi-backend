package io.soo.bueongi.core.share.domain.model

import java.time.Instant

data class ShareLocationUpdated(
    val updatedAt: Instant,
    val expiresAt: Instant,
)
