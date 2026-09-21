package io.soo.bueongi.core.share.domain.model

data class LocationReport(
    val lat: Double,
    val lng: Double,
    val remainingM: Double? = null,
    val remainingS: Double? = null,
)
