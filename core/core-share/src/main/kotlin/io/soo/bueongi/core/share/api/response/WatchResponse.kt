package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class WatchResponse(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    @param:JsonProperty("updated_at")
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    @param:JsonProperty("remaining_m")
    val remainingM: Double? = null,
    @param:JsonProperty("remaining_s")
    val remainingS: Double? = null,
)
