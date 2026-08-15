package io.soo.bueongi.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class CreateShareResponse(
    val token: String,
    @JsonProperty("owner_secret")
    val ownerSecret: String,
    @JsonProperty("share_url")
    val shareUrl: String,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)
