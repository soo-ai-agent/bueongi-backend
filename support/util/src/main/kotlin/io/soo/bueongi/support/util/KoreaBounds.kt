package io.soo.bueongi.support.util

fun isInKorea(lat: Double, lng: Double): Boolean {
    val bothFinite: Boolean = lat.isFinite() && lng.isFinite()
    val inLatitudeRange: Boolean = lat in KOREA_MIN_LAT..KOREA_MAX_LAT
    val inLongitudeRange: Boolean = lng in KOREA_MIN_LNG..KOREA_MAX_LNG
    return bothFinite && inLatitudeRange && inLongitudeRange
}

// 저장소 집계(SQL)도 같은 범위로 거른다.
const val KOREA_MIN_LAT: Double = 33.0
const val KOREA_MAX_LAT: Double = 39.0
const val KOREA_MIN_LNG: Double = 124.0
const val KOREA_MAX_LNG: Double = 132.0
