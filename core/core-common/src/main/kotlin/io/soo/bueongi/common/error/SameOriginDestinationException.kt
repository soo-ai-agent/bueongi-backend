package io.soo.bueongi.common.error

class SameOriginDestinationException(
    val distanceMeters: Int,
) : RuntimeException("출발지와 도착지가 동일하거나 너무 가깝습니다(거리 ${distanceMeters}m).")
