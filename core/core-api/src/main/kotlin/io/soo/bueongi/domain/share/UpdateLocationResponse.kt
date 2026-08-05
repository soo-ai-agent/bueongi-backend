package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

/** `POST /share/{token}/location`·`/route` 응답: 갱신 시각 + 공유 상한 만료. */
data class UpdateLocationResponse(
    @JsonProperty("updated_at")
    val updatedAt: Instant,
    @JsonProperty("expires_at")
    val expiresAt: Instant,
)
