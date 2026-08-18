package io.soo.bueongi.client.safe182

/** 조회할 사각 범위. 원천은 위경도 네 값을 모두 받거나 모두 안 받는다(일부만 주면 무시된다). */
data class MapBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)
