package io.soo.bueongi.core.domain.share

data class LocationReport(
    val lat: Double,
    val lng: Double,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
