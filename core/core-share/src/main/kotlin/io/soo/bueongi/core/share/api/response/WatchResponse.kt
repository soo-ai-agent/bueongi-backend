package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.share.domain.model.ShareWatchStatus
import java.time.Instant

/** 아직 보고가 없으면 위치·시각이 비어 있다. */
data class WatchResponse(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    @JsonProperty("updated_at")
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    @JsonProperty("remaining_m")
    val remainingM: Double? = null,
    @JsonProperty("remaining_s")
    val remainingS: Double? = null,
) {
    companion object {

        fun from(status: ShareWatchStatus): WatchResponse =
            WatchResponse(
            watching = status.watching,
            lat = status.lat,
            lng = status.lng,
            updatedAt = status.updatedAt,
            expired = status.expired,
            remainingM = status.remainingM,
            remainingS = status.remainingS,
        )
    }
}
