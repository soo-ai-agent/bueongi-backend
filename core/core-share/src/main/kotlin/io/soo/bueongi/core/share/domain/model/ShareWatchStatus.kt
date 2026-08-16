package io.soo.bueongi.core.share.domain.model

import java.time.Instant

/**
 * 보호자 관찰 상태. 응답 DTO 변환은 컨트롤러가 한다.
 */
data class ShareWatchStatus(
    val watching: Boolean,
    // 첫 위치 보고 전에는 null 이 가능함
    val lat: Double? = null,
    // 첫 위치 보고 전에는 null 이 가능함
    val lng: Double? = null,
    // 첫 위치 보고 전에는 null 이 가능함
    val updatedAt: Instant? = null,
    val expired: Boolean = false,
    // 길안내 중이 아니면 null 이 가능함
    val remainingM: Double? = null,
    // 길안내 중이 아니면 null 이 가능함
    val remainingS: Double? = null,
)
