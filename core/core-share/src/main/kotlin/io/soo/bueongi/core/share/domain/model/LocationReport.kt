package io.soo.bueongi.core.share.domain.model

/** 남은 거리·시간은 길안내 중에만 들어온다. */
data class LocationReport(
    val lat: Double,
    val lng: Double,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
