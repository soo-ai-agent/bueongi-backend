package io.soo.bueongi.core.share.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class LocationView(
    val lat: Double?,
    val lng: Double?,
    @param:JsonProperty("updated_at")
    val updatedAt: Instant?,
    val expired: Boolean,
    @param:JsonProperty("remaining_m")
    val remainingM: Double? = null,
    @param:JsonProperty("remaining_s")
    val remainingS: Double? = null,
    val overdue: Boolean = false,
) {
    companion object {
        // 좌표 필드는 와이어 계약상 null 로 나간다.
        val EXPIRED = LocationView(lat = null, lng = null, updatedAt = null, expired = true)

        fun waiting(overdue: Boolean): LocationView =
            LocationView(lat = null, lng = null, updatedAt = null, expired = false, overdue = overdue)
    }
}
