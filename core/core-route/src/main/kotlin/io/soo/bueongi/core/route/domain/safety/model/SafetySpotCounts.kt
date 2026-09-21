package io.soo.bueongi.core.route.domain.safety.model

data class SafetySpotCounts(
    val cctv: Int,
    val lamp: Int,
    val bell: Int,
    val safehouse: Int,
    val police: Int,
    val crimeProneArea: Int,
)
