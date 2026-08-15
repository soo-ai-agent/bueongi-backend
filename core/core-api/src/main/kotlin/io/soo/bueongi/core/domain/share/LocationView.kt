package io.soo.bueongi.core.domain.share

import com.fasterxml.jackson.annotation.JsonProperty
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
) {
    companion object {

        /** 세션이 없거나 만료됨. 좌표 필드는 와이어 계약상 null 로 나간다. */
        val EXPIRED: LocationView = LocationView(lat = null, lng = null, updatedAt = null, expired = true)

        /** 세션은 살아 있으나 아직 위치 보고가 없음. */
        fun waiting(overdue: Boolean): LocationView =
            LocationView(lat = null, lng = null, updatedAt = null, expired = false, overdue = overdue)
    }
}
