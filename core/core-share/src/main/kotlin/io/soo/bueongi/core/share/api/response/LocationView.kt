package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.storage.db.share.model.StoredLocation
import java.time.Instant

data class LocationView(
    val lat: Double?,
    val lng: Double?,
    @param:JsonProperty("updated_at")
    val updatedAt: Instant?,
    val expired: Boolean,
    @param:JsonProperty("remaining_meters")
    val remainingMeters: Double? = null,
    @param:JsonProperty("remaining_seconds")
    val remainingSeconds: Double? = null,
    val overdue: Boolean = false,
) {
    companion object {
        // 좌표 필드는 와이어 계약상 null 로 나간다.
        val EXPIRED = LocationView(lat = null, lng = null, updatedAt = null, expired = true)

        fun waiting(overdue: Boolean): LocationView {
            return LocationView(lat = null, lng = null, updatedAt = null, expired = false, overdue = overdue)
        }

        fun from(location: StoredLocation, overdue: Boolean): LocationView {
            return LocationView(
                lat = location.lat,
                lng = location.lng,
                updatedAt = location.updatedAt,
                expired = false,
                remainingMeters = location.remainingMeters,
                remainingSeconds = location.remainingSeconds,
                overdue = overdue,
            )
        }
    }
}
