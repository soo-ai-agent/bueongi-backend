package io.soo.bueongi.client.safemap

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

object Epsg3857 {
    private const val EARTH_RADIUS_METERS: Double = 6_378_137.0
    private const val MAX_COORDINATE_METERS: Double = 20_037_508.342789244

    fun toWgs84(x: Double, y: Double): Wgs84Point? {
        if (!x.isFinite() || !y.isFinite()) {
            return null
        }
        if (x !in -MAX_COORDINATE_METERS..MAX_COORDINATE_METERS ||
            y !in -MAX_COORDINATE_METERS..MAX_COORDINATE_METERS
        ) {
            return null
        }
        val lng: Double = Math.toDegrees(x / EARTH_RADIUS_METERS)
        val lat: Double = Math.toDegrees(2 * atan(exp(y / EARTH_RADIUS_METERS)) - PI / 2)
        return if (Epsg5179.inKoreaBounds(lat, lng)) {
            Wgs84Point(lat, lng)
        } else {
            null
        }
    }

    fun fromWgs84(lat: Double, lng: Double): TmXy {
        val x: Double = EARTH_RADIUS_METERS * Math.toRadians(lng)
        val y: Double = EARTH_RADIUS_METERS * ln(tan(PI / 4 + Math.toRadians(lat) / 2))
        return TmXy(x, y)
    }
}
