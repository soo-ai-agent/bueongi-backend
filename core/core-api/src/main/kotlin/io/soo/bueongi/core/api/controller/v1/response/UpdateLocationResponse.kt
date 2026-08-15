package io.soo.bueongi.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.domain.share.ShareLocationUpdated
import java.time.Instant

data class UpdateLocationResponse(
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
) {
    companion object {

        fun from(updated: ShareLocationUpdated): UpdateLocationResponse = UpdateLocationResponse(
            updatedAt = updated.updatedAt,
            expiresAt = updated.expiresAt,
        )
    }
}
