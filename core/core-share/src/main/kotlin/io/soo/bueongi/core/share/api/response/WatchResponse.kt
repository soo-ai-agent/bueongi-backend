package io.soo.bueongi.core.share.api.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.soo.bueongi.core.share.domain.model.ShareWatchStatus
import java.time.Instant

data class WatchResponse(
    val watching: Boolean,
    // 첫 위치 보고 전에는 null 이 가능함
    val lat: Double? = null,
    // 첫 위치 보고 전에는 null 이 가능함
    val lng: Double? = null,
    @param:JsonProperty("updated_at")
    // 첫 위치 보고 전에는 null 이 가능함
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    @param:JsonProperty("remaining_m")
    // 길안내 중이 아니면 null 이 가능함
    val remainingM: Double? = null,
    @param:JsonProperty("remaining_s")
    // 길안내 중이 아니면 null 이 가능함
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
