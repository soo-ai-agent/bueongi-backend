package io.soo.bueongi.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.core.share.StoredRoute
import java.time.Instant

data class LocationView(
    val lat: Double?,
    val lng: Double?,
    @JsonProperty("updated_at")
    val updatedAt: Instant?,
    val expired: Boolean,
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
    val overdue: Boolean = false,
)
