package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.share.model.ShareSession
import java.time.Instant

data class UpdateLocationResponse(
    @param:JsonProperty("updated_at")
    val updatedAt: Instant,
    @param:JsonProperty("expires_at")
    val expiresAt: Instant,
) {
    companion object {
        fun from(session: ShareSession, updatedAt: Instant): UpdateLocationResponse {
            return UpdateLocationResponse(updatedAt = updatedAt, expiresAt = session.expiresAt)
        }
    }
}
