package io.soo.bueongi.support.util

fun resolveKoreanWgs84OrNull(xText: String?, yText: String?): Wgs84Point? {
    val x: Double = xText?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val y: Double = yText?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return when {
        isInKorea(lat = y, lng = x) -> Wgs84Point(lat = y, lng = x)
        isInKorea(lat = x, lng = y) -> Wgs84Point(lat = x, lng = y) // 순서 뒤바뀜 방어
        x in UTMK_EASTING_RANGE && y in UTMK_NORTHING_RANGE -> Epsg5179.toWgs84(x, y)
        x in WEB_MERCATOR_KOREA_X_RANGE && y in WEB_MERCATOR_KOREA_Y_RANGE -> Epsg3857.toWgs84(x, y)
        else -> null
    }
}

private val UTMK_EASTING_RANGE: ClosedFloatingPointRange<Double> = 400_000.0..1_600_000.0
private val UTMK_NORTHING_RANGE: ClosedFloatingPointRange<Double> = 1_100_000.0..2_900_000.0
private val WEB_MERCATOR_KOREA_X_RANGE: ClosedFloatingPointRange<Double> = 13_800_000.0..14_700_000.0
private val WEB_MERCATOR_KOREA_Y_RANGE: ClosedFloatingPointRange<Double> = 3_800_000.0..4_800_000.0
