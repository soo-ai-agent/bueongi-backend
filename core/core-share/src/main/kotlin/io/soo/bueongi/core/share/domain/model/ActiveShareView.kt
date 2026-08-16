package io.soo.bueongi.core.share.domain.model


/**
 * 어드민 응답 계약은 camelCase — 보호자용 snake_case 결과들과 달리 @JsonProperty 를 붙이지 않는다.
 * 아직 보고가 없으면 위치·시각이 비어 있다.
 */
data class ActiveShareView(
    val token: String,
    val shareUrl: String,
    val lat: Double?,
    val lng: Double?,
    val updatedAtMs: Long?,
    val ttlSeconds: Long,
    val viewerActive: Boolean,
)
