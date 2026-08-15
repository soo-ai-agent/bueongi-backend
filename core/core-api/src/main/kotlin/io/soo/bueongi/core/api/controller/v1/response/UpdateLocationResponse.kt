package io.soo.bueongi.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class UpdateLocationResponse(
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)
