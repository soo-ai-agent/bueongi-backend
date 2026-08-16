package io.soo.bueongi.core.share.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class LocationView(
    // 첫 위치 보고 전에는 null 이 가능함
    val lat: Double?,
    // 첫 위치 보고 전에는 null 이 가능함
    val lng: Double?,
    @param:JsonProperty("updated_at")
    // 첫 위치 보고 전에는 null 이 가능함
    val updatedAt: Instant?,
    val expired: Boolean,
    @param:JsonProperty("remaining_m")
    // 길안내 중이 아니면 null 이 가능함
    val remainingM: Double? = null,
    @param:JsonProperty("remaining_s")
    // 길안내 중이 아니면 null 이 가능함
    val remainingS: Double? = null,
    val overdue: Boolean = false,
) {
    companion object {

        /** 세션이 없거나 만료됨. 좌표 필드는 와이어 계약상 null 로 나간다. */
        val EXPIRED = LocationView(lat = null, lng = null, updatedAt = null, expired = true)

        /** 세션은 살아 있으나 아직 위치 보고가 없음. */
        fun waiting(overdue: Boolean): LocationView =
            LocationView(lat = null, lng = null, updatedAt = null, expired = false, overdue = overdue)
    }
}
