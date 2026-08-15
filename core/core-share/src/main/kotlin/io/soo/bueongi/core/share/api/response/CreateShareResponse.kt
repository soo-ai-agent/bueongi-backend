package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.share.domain.model.ShareCreated
import java.time.Instant

data class CreateShareResponse(
    val token: String,
    @JsonProperty("owner_secret")
    val ownerSecret: String,
    @JsonProperty("share_url")
    val shareUrl: String,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
) {
    companion object {

        fun from(created: ShareCreated): CreateShareResponse = CreateShareResponse(
            token = created.token,
            ownerSecret = created.ownerSecret,
            shareUrl = created.shareUrl,
            expiresAt = created.expiresAt,
        )
    }
}
