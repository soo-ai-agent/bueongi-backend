package io.soo.bueongi.client.safemap

/**
 * 좌표계가 응답에 명시되지 않은 국가 안전데이터(생활안전지도·재난안전데이터 공통POI 등)의 x/y 문자열 쌍을
 * WGS84 로 판별·변환한다. 값 범위로 자동 판별한다 — 경위도(WGS84)면 그대로, 순서가 뒤바뀌었으면 교정,
 * UTM-K(EPSG:5179) 미터 범위면 [Epsg5179] 역변환. 어느 쪽도 아니면 null(그 행은 상위에서 버린다).
 *
 * 경위도(±180/±90)와 UTM-K 미터(수십만~백만대)는 자릿수가 달라 범위가 겹치지 않으므로 판별이 모호하지 않다.
 * safemap 치안시설과 safetydata 가로등이 같은 규칙을 쓰므로 여기 한 곳에 둔다(중복 방지).
 */
internal fun resolveKoreanWgs84OrNull(xText: String?, yText: String?): Wgs84Point? {
    val x: Double = xText?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val y: Double = yText?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    return when {
        Epsg5179.inKoreaBounds(lat = y, lng = x) -> Wgs84Point(lat = y, lng = x) // x=경도, y=위도
        Epsg5179.inKoreaBounds(lat = x, lng = y) -> Wgs84Point(lat = x, lng = y) // 순서 뒤바뀜 방어
        x in UTMK_EASTING_RANGE && y in UTMK_NORTHING_RANGE -> Epsg5179.toWgs84(x, y)
        else -> null
    }
}

/**
 * EPSG:5179 로 판별하는 동경(easting)/북위(northing) 미터 범위 — false easting/northing
 * (1,000,000/2,000,000m) 기준 한반도 유효 범위. 경위도와 자릿수가 달라 겹치지 않는다.
 */
private val UTMK_EASTING_RANGE: ClosedFloatingPointRange<Double> = 400_000.0..1_600_000.0
private val UTMK_NORTHING_RANGE: ClosedFloatingPointRange<Double> = 1_100_000.0..2_900_000.0
