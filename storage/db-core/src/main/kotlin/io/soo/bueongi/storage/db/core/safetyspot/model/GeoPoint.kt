package io.soo.bueongi.storage.db.core.safetyspot.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 프로퍼티명(lat/lng)은 RouteOption.path 직렬화로 노출되는 프론트 응답 계약 — 리네임 금지. */
data class GeoPoint(val lat: Double, val lng: Double) {

    fun distanceMetersTo(other: GeoPoint): Double {
        val dLat: Double = Math.toRadians(other.lat - lat)
        val dLng: Double = Math.toRadians(other.lng - lng)
        val h: Double = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    /** 선분 [a]–[b] 위로 내린 수선. 국소 평면(equirectangular) 근사라 도심 규모(수 km)에서만 유효. */
    fun projectOnto(a: GeoPoint, b: GeoPoint): SegmentProjection {
        val self: Planar = toPlanar(lat)
        val pa: Planar = a.toPlanar(lat)
        val pb: Planar = b.toPlanar(lat)
        val dx: Double = pb.x - pa.x
        val dy: Double = pb.y - pa.y
        val segLenSq: Double = dx * dx + dy * dy
        if (segLenSq == 0.0) {
            return SegmentProjection(t = 0.0, distanceMeters = hypot(self.x - pa.x, self.y - pa.y))
        }
        val tRaw: Double = ((self.x - pa.x) * dx + (self.y - pa.y) * dy) / segLenSq
        val t: Double = max(0.0, min(1.0, tRaw))
        return SegmentProjection(
            t = tRaw,
            distanceMeters = hypot(self.x - (pa.x + t * dx), self.y - (pa.y + t * dy)),
        )
    }

    fun distanceMetersToSegment(a: GeoPoint, b: GeoPoint): Double =
        projectOnto(a, b).distanceMeters

    fun distanceMetersToPath(path: List<GeoPoint>): Double {
        if (path.isEmpty()) {
            return Double.POSITIVE_INFINITY
        }
        if (path.size == 1) {
            return distanceMetersTo(path[0])
        }
        var nearest: Double = Double.POSITIVE_INFINITY
        for (i in 1 until path.size) {
            val d: Double = distanceMetersToSegment(path[i - 1], path[i])
            if (d < nearest) {
                nearest = d
            }
        }
        return nearest
    }

    private fun toPlanar(originLat: Double): Planar {
        val latScale: Double = Math.toRadians(1.0) * EARTH_RADIUS_M
        val lngScale: Double = latScale * cos(Math.toRadians(originLat))
        return Planar(x = lng * lngScale, y = lat * latScale)
    }

    private data class Planar(val x: Double, val y: Double)

    companion object {

        private const val EARTH_RADIUS_M: Double = 6_371_000.0

        fun orNull(lat: Double?, lng: Double?): GeoPoint? {
            if (lat == null || !lat.isFinite() || lat !in -90.0..90.0) {
                return null
            }
            if (lng == null || !lng.isFinite() || lng !in -180.0..180.0) {
                return null
            }
            return GeoPoint(lat, lng)
        }
    }
}

/** 이어진 좌표들의 총 길이(m). */
fun List<GeoPoint>.pathLengthMeters(): Double {
    var total: Double = 0.0
    for (i in 1 until size) {
        total += this[i - 1].distanceMetersTo(this[i])
    }
    return total
}

/** 이 경로 중 [other] 에서 [withinMeters] 이내로 겹치는 구간의 길이(m). */
fun List<GeoPoint>.overlapMetersWith(other: List<GeoPoint>, withinMeters: Double): Double {
    if (size < 2 || other.size < 2) {
        return 0.0
    }
    var overlap: Double = 0.0
    for (i in 1 until size) {
        val a: GeoPoint = this[i - 1]
        val b: GeoPoint = this[i]
        val mid: GeoPoint = GeoPoint((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
        if (mid.distanceMetersToPath(other) <= withinMeters) {
            overlap += a.distanceMetersTo(b)
        }
    }
    return overlap
}
