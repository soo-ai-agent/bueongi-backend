package io.soo.bueongi.core.share.api.response

import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredLocation

// 어드민 응답 계약은 camelCase — 보호자용 snake_case 결과들과 달리 @JsonProperty 를 붙이지 않는다.
// lat·lng·updatedAtMs 는 위치를 아직 보고하지 않은 세션이면 null 로 나간다(목록 정렬에서 맨 뒤).
data class ActiveShareView(
    val token: String,
    val shareUrl: String,
    val lat: Double?,
    val lng: Double?,
    val updatedAtMs: Long?,
    val ttlSeconds: Long,
    val viewerActive: Boolean,
) {
    companion object {
        fun from(session: ShareSession, shareUrl: String, ttlSeconds: Long, viewerActive: Boolean): ActiveShareView {
            val location: StoredLocation? = session.location
            return ActiveShareView(
                token = session.token,
                shareUrl = shareUrl,
                lat = location?.lat,
                lng = location?.lng,
                updatedAtMs = location?.updatedAt?.toEpochMilli(),
                ttlSeconds = ttlSeconds,
                viewerActive = viewerActive,
            )
        }
    }
}
