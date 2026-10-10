package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.model.ShareSession
import java.time.Instant

data class CreateShareResponse(
    val token: String,
    @param:JsonProperty("owner_secret")
    val ownerSecret: String,
    @param:JsonProperty("share_url")
    val shareUrl: String,
    @param:JsonProperty("expires_at")
    val expiresAt: Instant,
) {
    companion object {
        fun from(session: ShareSession, shareUrl: String): CreateShareResponse {
            return CreateShareResponse(
                token = session.token,
                ownerSecret = session.ownerSecret,
                shareUrl = shareUrl,
                expiresAt = session.expiresAt,
            )
        }
    }
}
