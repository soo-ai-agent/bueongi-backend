package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class UpdateLocationResponse(
    @param:JsonProperty("updated_at")
    val updatedAt: Instant,
    @param:JsonProperty("expires_at")
    val expiresAt: Instant,
)
