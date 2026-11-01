package io.soo.bueongi.core.share.domain.model

data class LocationReport(
    val lat: Double,
    val lng: Double,
    val remainingMeters: Double? = null,
    val remainingSeconds: Double? = null,
)
