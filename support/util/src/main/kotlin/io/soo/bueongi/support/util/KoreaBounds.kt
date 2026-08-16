package io.soo.bueongi.support.util

/** 좌표 변환 결과가 한반도 안인지 본다. 범위는 파이프라인 KOREA_BOUNDS 와 같은 값이다. */

fun isInKorea(lat: Double, lng: Double): Boolean {
    val bothFinite: Boolean = lat.isFinite() && lng.isFinite()
    val inLatitudeRange: Boolean = lat in MIN_LAT..MAX_LAT
    val inLongitudeRange: Boolean = lng in MIN_LNG..MAX_LNG
    return bothFinite && inLatitudeRange && inLongitudeRange
}

private const val MIN_LAT: Double = 33.0
private const val MAX_LAT: Double = 39.0
private const val MIN_LNG: Double = 124.0
private const val MAX_LNG: Double = 132.0
