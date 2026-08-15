package io.soo.bueongi.support.util

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** EPSG:5179(UTM-K) — proj4 `+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 +ellps=GRS80` 과 동일 파라미터의 Snyder 급수 구현. */
object Epsg5179 {
    private const val A: Double = 6378137.0 // GRS80 장반경(m)
    private const val F: Double = 1.0 / 298.257222101 // GRS80 편평률
    private const val K0: Double = 0.9996
    private const val FALSE_EASTING: Double = 1_000_000.0
    private const val FALSE_NORTHING: Double = 2_000_000.0
    private val LAT0: Double = Math.toRadians(38.0)
    private val LON0: Double = Math.toRadians(127.5)

    private const val E2: Double = F * (2 - F) // 제1이심률²
    private const val EP2: Double = E2 / (1 - E2) // 제2이심률²
    private val E1: Double = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))
    private val M0: Double = meridianArc(LAT0)

    /** 파이프라인 KOREA_BOUNDS 와 동일 값. */
    fun inKoreaBounds(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() && lat in 33.0..39.0 && lng in 124.0..132.0

    /** x=동(easting), y=북(northing). */
    fun toWgs84(x: Double, y: Double): Wgs84Point? {
        if (!x.isFinite() || !y.isFinite()) {
            return null
        }
        val m: Double = M0 + (y - FALSE_NORTHING) / K0
        val mu: Double = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256))
        val phi1: Double = mu +
            (3 * E1 / 2 - 27 * E1.pow(3) / 32) * sin(2 * mu) +
            (21 * E1.pow(2) / 16 - 55 * E1.pow(4) / 32) * sin(4 * mu) +
            (151 * E1.pow(3) / 96) * sin(6 * mu) +
            (1097 * E1.pow(4) / 512) * sin(8 * mu)

        val sinPhi1: Double = sin(phi1)
        val cosPhi1: Double = cos(phi1)
        val tanPhi1: Double = tan(phi1)
        val c1: Double = EP2 * cosPhi1 * cosPhi1
        val t1: Double = tanPhi1 * tanPhi1
        val n1: Double = A / sqrt(1 - E2 * sinPhi1 * sinPhi1)
        val r1: Double = A * (1 - E2) / (1 - E2 * sinPhi1 * sinPhi1).pow(1.5)
        val d: Double = (x - FALSE_EASTING) / (n1 * K0)

        val lat: Double = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d.pow(6) / 720
            )
        val lon: Double = LON0 + (
            d -
                (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d.pow(5) / 120
            ) / cosPhi1

        val latDeg: Double = Math.toDegrees(lat)
        val lngDeg: Double = Math.toDegrees(lon)
        return if (inKoreaBounds(latDeg, lngDeg)) {
            Wgs84Point(latDeg, lngDeg)
        } else {
            null
        }
    }

    fun fromWgs84(lat: Double, lng: Double): TmXy {
        val phi: Double = Math.toRadians(lat)
        val sinPhi: Double = sin(phi)
        val cosPhi: Double = cos(phi)
        val tanPhi: Double = tan(phi)
        val n: Double = A / sqrt(1 - E2 * sinPhi * sinPhi)
        val t: Double = tanPhi * tanPhi
        val c: Double = EP2 * cosPhi * cosPhi
        val aa: Double = (Math.toRadians(lng) - LON0) * cosPhi

        val x: Double = FALSE_EASTING + K0 * n * (
            aa +
                (1 - t + c) * aa.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * EP2) * aa.pow(5) / 120
            )
        val y: Double = FALSE_NORTHING + K0 * (
            meridianArc(phi) - M0 + n * tanPhi * (
                aa * aa / 2 +
                    (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * EP2) * aa.pow(6) / 720
                )
            )
        return TmXy(x, y)
    }

    private fun meridianArc(phi: Double): Double = A * (
        (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * phi -
            (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * phi) +
            (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * phi) -
            (35 * E2 * E2 * E2 / 3072) * sin(6 * phi)
        )
}
