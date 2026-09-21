package io.soo.bueongi.core.share.domain.model

import java.time.Instant

data class ShareCreated(
    val token: String,
    val ownerSecret: String,
    val shareUrl: String,
    val expiresAt: Instant,
)
