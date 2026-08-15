package io.soo.bueongi.core.support.error

class TripTooFarException(
    val distanceMeters: Int,
    val maxMeters: Int,
) : RuntimeException("출발지와 도착지가 너무 멉니다(거리 ${distanceMeters}m, 최대 ${maxMeters}m).")
