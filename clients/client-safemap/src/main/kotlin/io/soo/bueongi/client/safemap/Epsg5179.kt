package io.soo.bueongi.client.safemap

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** WGS84 한 점(치안시설 좌표 변환 결과). */
data class Wgs84Point(val lat: Double, val lng: Double)

/** 투영 평면 좌표(TM): [x]=동쪽(easting, m), [y]=북쪽(northing, m). [Epsg5179]·[Epsg5174] 정투영 결과. */
data class TmXy(val x: Double, val y: Double)

/**
 * EPSG:5179(UTM-K, GRS80) ↔ WGS84 변환 — 치안시설(IF_0036)의 x/y 좌표용.
 *
 * 파이프라인의 proj4 정의(`+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000
 * +y_0=2000000 +ellps=GRS80`)와 동일 파라미터를 Snyder 횡단 메르카토르 급수로 구현했다.
 * 급수 절단 오차는 한반도 범위에서 cm 미만 — 시설 마커·안전점수 용도에 충분하다.
 * 검증: 원점 앵커(38°/127.5° ↔ 1,000,000/2,000,000) + 왕복(forward→inverse) 테스트.
 */
object Epsg5179 {
    private const val A = 6378137.0 // GRS80 장반경(m)
    private const val F = 1.0 / 298.257222101 // GRS80 편평률
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 1_000_000.0
    private const val FALSE_NORTHING = 2_000_000.0
    private val LAT0 = Math.toRadians(38.0)
    private val LON0 = Math.toRadians(127.5)

    private const val E2 = F * (2 - F) // 제1이심률²
    private const val EP2 = E2 / (1 - E2) // 제2이심률²
    private val E1 = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))
    private val M0 = meridianArc(LAT0)

    /** 대한민국 WGS84 유효 범위(파이프라인 KOREA_BOUNDS와 동일). 밖이면 변환 오류로 보고 폐기. */
    fun inKoreaBounds(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() && lat in 33.0..39.0 && lng in 124.0..132.0

    /** EPSG:5179 (x=동, y=북) → WGS84. 결과가 한국 범위 밖이면 null(원천 좌표 불량). */
    fun toWgs84(x: Double, y: Double): Wgs84Point? {
        if (!x.isFinite() || !y.isFinite()) {
            return null
        }
        val m = M0 + (y - FALSE_NORTHING) / K0
        val mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256))
        val phi1 = mu +
            (3 * E1 / 2 - 27 * E1.pow(3) / 32) * sin(2 * mu) +
            (21 * E1.pow(2) / 16 - 55 * E1.pow(4) / 32) * sin(4 * mu) +
            (151 * E1.pow(3) / 96) * sin(6 * mu) +
            (1097 * E1.pow(4) / 512) * sin(8 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)
        val c1 = EP2 * cosPhi1 * cosPhi1
        val t1 = tanPhi1 * tanPhi1
        val n1 = A / sqrt(1 - E2 * sinPhi1 * sinPhi1)
        val r1 = A * (1 - E2) / (1 - E2 * sinPhi1 * sinPhi1).pow(1.5)
        val d = (x - FALSE_EASTING) / (n1 * K0)

        val lat = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d.pow(6) / 720
            )
        val lon = LON0 + (
            d -
                (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d.pow(5) / 120
            ) / cosPhi1

        val latDeg = Math.toDegrees(lat)
        val lngDeg = Math.toDegrees(lon)
        return if (inKoreaBounds(latDeg, lngDeg)) {
            Wgs84Point(latDeg, lngDeg)
        } else {
            null
        }
    }

    /** WGS84 → EPSG:5179. 왕복 테스트(변환 자기일관성 검증)용. */
    fun fromWgs84(lat: Double, lng: Double): TmXy {
        val phi = Math.toRadians(lat)
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)
        val n = A / sqrt(1 - E2 * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = EP2 * cosPhi * cosPhi
        val aa = (Math.toRadians(lng) - LON0) * cosPhi

        val x = FALSE_EASTING + K0 * n * (
            aa +
                (1 - t + c) * aa.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * EP2) * aa.pow(5) / 120
            )
        val y = FALSE_NORTHING + K0 * (
            meridianArc(phi) - M0 + n * tanPhi * (
                aa * aa / 2 +
                    (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * EP2) * aa.pow(6) / 720
                )
            )
        return TmXy(x, y)
    }

    /** 적도에서 위도 [phi]까지의 자오선 호 길이(m) — Snyder 급수. */
    private fun meridianArc(phi: Double): Double = A * (
        (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * phi -
            (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * phi) +
            (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * phi) -
            (35 * E2 * E2 * E2 / 3072) * sin(6 * phi)
        )
}
