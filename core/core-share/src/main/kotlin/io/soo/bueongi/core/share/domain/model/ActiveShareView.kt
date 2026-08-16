package io.soo.bueongi.core.share.domain.model


/**
 * 어드민 응답 계약은 camelCase — 보호자용 snake_case 결과들과 달리 @JsonProperty 를 붙이지 않는다.
 */
data class ActiveShareView(
    val token: String,
    val shareUrl: String,
    // 첫 위치 보고 전에는 null 이 가능함
    val lat: Double?,
    // 첫 위치 보고 전에는 null 이 가능함
    val lng: Double?,
    // 첫 위치 보고 전에는 null 이 가능함
    val updatedAtMs: Long?,
    val ttlSeconds: Long,
    val viewerActive: Boolean,
)
