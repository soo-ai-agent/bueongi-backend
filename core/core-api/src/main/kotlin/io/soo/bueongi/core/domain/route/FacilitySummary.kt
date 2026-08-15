package io.soo.bueongi.core.domain.route

data class FacilitySummary(
    val cctv: Int,
    val bell: Int,
    val store: Int,
    val police: Int,
    val safehouse: Int,
    val lamp: Int,
    val pedestrianSignal: Int,
    val total: Int,
)
