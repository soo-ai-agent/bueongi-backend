package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.share.model.StoredLocation
import java.time.Instant

data class WatchResponse(
    val watching: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    @param:JsonProperty("updated_at")
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    @param:JsonProperty("remaining_meters")
    val remainingMeters: Double? = null,
    @param:JsonProperty("remaining_seconds")
    val remainingSeconds: Double? = null,
) {
    companion object {
        val EXPIRED = WatchResponse(watching = false, expired = true)

        // 보호자가 보고 있어도 아직 위치를 한 번도 올리지 않았으면 location 은 null.
        fun from(watching: Boolean, location: StoredLocation?): WatchResponse {
            return WatchResponse(
                watching = watching,
                lat = location?.lat,
                lng = location?.lng,
                updatedAt = location?.updatedAt,
                expired = false,
                remainingMeters = location?.remainingMeters,
                remainingSeconds = location?.remainingSeconds,
            )
        }
    }
}
