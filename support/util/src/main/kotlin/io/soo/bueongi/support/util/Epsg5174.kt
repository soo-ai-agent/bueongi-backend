package io.soo.bueongi.support.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** EPSG:5174 — proj4 `+proj=tmerc +lat_0=38 +lon_0=127.0028902777778 +k=1 +x_0=200000 +y_0=500000 +ellps=bessel +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43` 과 동일 파라미터, datum shift 는 위치벡터(Position Vector) 회전 규약. */
object Epsg5174 {
    // Bessel 1841 타원체(원천 좌표계).
    private const val A_BESSEL: Double = 6377397.155
    private const val F_BESSEL: Double = 1.0 / 299.1528128

    // WGS84 타원체(결과 좌표계).
    private const val A_WGS84: Double = 6378137.0
    private const val F_WGS84: Double = 1.0 / 298.257223563

    // 중부원점 TM 투영 파라미터.
    private const val K0: Double = 1.0
    private const val FALSE_EASTING: Double = 200000.0
    private const val FALSE_NORTHING: Double = 500000.0
    private val LAT0: Double = Math.toRadians(38.0)
    private val LON0: Double = Math.toRadians(127.0028902777778)

    // 7-parameter datum shift(Bessel→WGS84), proj4 +towgs84 순서: dx,dy,dz(m), rx,ry,rz(arcsec), ds(ppm).
    private const val DX: Double = -115.80
    private const val DY: Double = 474.99
    private const val DZ: Double = 674.11
    private val RX: Double = arcsecToRad(1.16)
    private val RY: Double = arcsecToRad(-2.31)
    private val RZ: Double = arcsecToRad(-1.63)
    private const val DS: Double = 6.43 * 1e-6

    private const val E2_BESSEL: Double = F_BESSEL * (2 - F_BESSEL)
    private const val EP2_BESSEL: Double = E2_BESSEL / (1 - E2_BESSEL)
    private val E1_BESSEL: Double = (1 - sqrt(1 - E2_BESSEL)) / (1 + sqrt(1 - E2_BESSEL))
    private val M0_BESSEL: Double = meridianArc(LAT0)

    private const val E2_WGS84: Double = F_WGS84 * (2 - F_WGS84)

    private const val BOWRING_ITERATIONS: Int = 5

    /** x=동(easting), y=북(northing). */
    fun toWgs84(x: Double, y: Double): Wgs84Point? {
        if (!x.isFinite() || !y.isFinite()) {
            return null
        }
        val besselGeodetic: Geodetic = inverseTm(x, y)
        val besselEcef: Ecef = geodeticToEcef(besselGeodetic.latRad, besselGeodetic.lngRad, A_BESSEL, E2_BESSEL)
        val wgsEcef: Ecef = helmertBesselToWgs84(besselEcef)
        val wgsGeodetic: Geodetic = ecefToGeodetic(wgsEcef, A_WGS84, E2_WGS84)

        val latDeg: Double = Math.toDegrees(wgsGeodetic.latRad)
        val lngDeg: Double = Math.toDegrees(wgsGeodetic.lngRad)
        return if (isInKorea(latDeg, lngDeg)) {
            Wgs84Point(latDeg, lngDeg)
        } else {
            null
        }
    }

    /** 지오이드고 미상이라 양방향 모두 h=0 가정 — 수평 왕복 오차는 무시 수준. */
    fun fromWgs84(lat: Double, lng: Double): TmXy {
        val wgsEcef: Ecef = geodeticToEcef(Math.toRadians(lat), Math.toRadians(lng), A_WGS84, E2_WGS84)
        val besselEcef: Ecef = helmertWgs84ToBessel(wgsEcef)
        val besselGeodetic: Geodetic = ecefToGeodetic(besselEcef, A_BESSEL, E2_BESSEL)
        return forwardTm(besselGeodetic.latRad, besselGeodetic.lngRad)
    }

    private fun inverseTm(x: Double, y: Double): Geodetic {
        val m: Double = M0_BESSEL + (y - FALSE_NORTHING) / K0
        val mu: Double = m / (A_BESSEL * (1 - E2_BESSEL / 4 - 3 * E2_BESSEL * E2_BESSEL / 64 - 5 * E2_BESSEL * E2_BESSEL * E2_BESSEL / 256))
        val phi1: Double = mu +
            (3 * E1_BESSEL / 2 - 27 * E1_BESSEL.pow(3) / 32) * sin(2 * mu) +
            (21 * E1_BESSEL.pow(2) / 16 - 55 * E1_BESSEL.pow(4) / 32) * sin(4 * mu) +
            (151 * E1_BESSEL.pow(3) / 96) * sin(6 * mu) +
            (1097 * E1_BESSEL.pow(4) / 512) * sin(8 * mu)

        val sinPhi1: Double = sin(phi1)
        val cosPhi1: Double = cos(phi1)
        val tanPhi1: Double = tan(phi1)
        val c1: Double = EP2_BESSEL * cosPhi1 * cosPhi1
        val t1: Double = tanPhi1 * tanPhi1
        val n1: Double = A_BESSEL / sqrt(1 - E2_BESSEL * sinPhi1 * sinPhi1)
        val r1: Double = A_BESSEL * (1 - E2_BESSEL) / (1 - E2_BESSEL * sinPhi1 * sinPhi1).pow(1.5)
        val d: Double = (x - FALSE_EASTING) / (n1 * K0)

        val lat: Double = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2_BESSEL) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2_BESSEL - 3 * c1 * c1) * d.pow(6) / 720
            )
        val lng: Double = LON0 + (
            d -
                (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2_BESSEL + 24 * t1 * t1) * d.pow(5) / 120
            ) / cosPhi1
        return Geodetic(lat, lng)
    }

    private fun forwardTm(latRad: Double, lngRad: Double): TmXy {
        val sinPhi: Double = sin(latRad)
        val cosPhi: Double = cos(latRad)
        val tanPhi: Double = tan(latRad)
        val n: Double = A_BESSEL / sqrt(1 - E2_BESSEL * sinPhi * sinPhi)
        val t: Double = tanPhi * tanPhi
        val c: Double = EP2_BESSEL * cosPhi * cosPhi
        val aa: Double = (lngRad - LON0) * cosPhi

        val x: Double = FALSE_EASTING + K0 * n * (
            aa +
                (1 - t + c) * aa.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * EP2_BESSEL) * aa.pow(5) / 120
            )
        val y: Double = FALSE_NORTHING + K0 * (
            meridianArc(latRad) - M0_BESSEL + n * tanPhi * (
                aa * aa / 2 +
                    (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * EP2_BESSEL) * aa.pow(6) / 720
                )
            )
        return TmXy(x, y)
    }

    private fun geodeticToEcef(latRad: Double, lngRad: Double, a: Double, e2: Double): Ecef {
        val sinLat: Double = sin(latRad)
        val cosLat: Double = cos(latRad)
        val n: Double = a / sqrt(1 - e2 * sinLat * sinLat)
        val x: Double = n * cosLat * cos(lngRad)
        val y: Double = n * cosLat * sin(lngRad)
        val z: Double = n * (1 - e2) * sinLat
        return Ecef(x, y, z)
    }

    private fun ecefToGeodetic(ecef: Ecef, a: Double, e2: Double): Geodetic {
        val p: Double = sqrt(ecef.x * ecef.x + ecef.y * ecef.y)
        val lng: Double = atan2(ecef.y, ecef.x)
        var lat: Double = atan2(ecef.z, p * (1 - e2))
        repeat(BOWRING_ITERATIONS) {
            val sinLat: Double = sin(lat)
            val n: Double = a / sqrt(1 - e2 * sinLat * sinLat)
            val h: Double = p / cos(lat) - n
            lat = atan2(ecef.z, p * (1 - e2 * n / (n + h)))
        }
        return Geodetic(lat, lng)
    }

    private fun helmertBesselToWgs84(v: Ecef): Ecef {
        val s: Double = 1 + DS
        return Ecef(
            x = DX + s * (v.x - RZ * v.y + RY * v.z),
            y = DY + s * (RZ * v.x + v.y - RX * v.z),
            z = DZ + s * (-RY * v.x + RX * v.y + v.z),
        )
    }

    private fun helmertWgs84ToBessel(v: Ecef): Ecef {
        val s: Double = 1 - DS
        return Ecef(
            x = -DX + s * (v.x + RZ * v.y - RY * v.z),
            y = -DY + s * (-RZ * v.x + v.y + RX * v.z),
            z = -DZ + s * (RY * v.x - RX * v.y + v.z),
        )
    }

    private fun meridianArc(phi: Double): Double =
        A_BESSEL * (
        (1 - E2_BESSEL / 4 - 3 * E2_BESSEL * E2_BESSEL / 64 - 5 * E2_BESSEL * E2_BESSEL * E2_BESSEL / 256) * phi -
            (3 * E2_BESSEL / 8 + 3 * E2_BESSEL * E2_BESSEL / 32 + 45 * E2_BESSEL * E2_BESSEL * E2_BESSEL / 1024) * sin(2 * phi) +
            (15 * E2_BESSEL * E2_BESSEL / 256 + 45 * E2_BESSEL * E2_BESSEL * E2_BESSEL / 1024) * sin(4 * phi) -
            (35 * E2_BESSEL * E2_BESSEL * E2_BESSEL / 3072) * sin(6 * phi)
        )

    private data class Geodetic(val latRad: Double, val lngRad: Double)

    private data class Ecef(val x: Double, val y: Double, val z: Double)
}

private fun arcsecToRad(arcsec: Double): Double =
    Math.toRadians(arcsec / 3600.0)
