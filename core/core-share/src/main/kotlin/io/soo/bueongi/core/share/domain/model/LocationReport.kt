package io.soo.bueongi.core.share.domain.model

data class LocationReport(
    val lat: Double,
    val lng: Double,
    // 길안내 중이 아니면 null 이 가능함
    val remainingM: Double? = null,
    // 길안내 중이 아니면 null 이 가능함
    val remainingS: Double? = null,
)
