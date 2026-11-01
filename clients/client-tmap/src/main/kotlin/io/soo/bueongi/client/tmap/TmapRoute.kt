package io.soo.bueongi.client.tmap

data class TmapRoute(
    val searchOption: String,
    val path: List<TmapCoord>,
    val distanceMeters: Double,
    val timeSeconds: Int,
    val steps: List<NavStep>,
)
