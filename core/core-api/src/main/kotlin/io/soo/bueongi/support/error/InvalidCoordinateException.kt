package io.soo.bueongi.support.error

class InvalidCoordinateException(
    val lat: Double,
    val lng: Double,
) : RuntimeException("위경도 범위가 올바르지 않습니다.")
