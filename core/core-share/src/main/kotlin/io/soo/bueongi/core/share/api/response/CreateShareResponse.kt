package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class CreateShareResponse(
    val token: String,
    @param:JsonProperty("owner_secret")
    val ownerSecret: String,
    @param:JsonProperty("share_url")
    val shareUrl: String,
    @param:JsonProperty("expires_at")
    val expiresAt: Instant,
)
