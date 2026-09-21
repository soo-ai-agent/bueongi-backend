package io.soo.bueongi.support.util

fun isInKorea(
    lat: Double,
    lng: Double,
): Boolean {
    val bothFinite: Boolean = lat.isFinite() && lng.isFinite()
    val inLatitudeRange: Boolean = lat in MIN_LAT..MAX_LAT
    val inLongitudeRange: Boolean = lng in MIN_LNG..MAX_LNG
    return bothFinite && inLatitudeRange && inLongitudeRange
}

private const val MIN_LAT: Double = 33.0
private const val MAX_LAT: Double = 39.0
private const val MIN_LNG: Double = 124.0
private const val MAX_LNG: Double = 132.0
