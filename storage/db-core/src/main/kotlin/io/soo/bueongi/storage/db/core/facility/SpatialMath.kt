package io.soo.bueongi.storage.db.core.facility

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 점-선분 계산은 국소 평면(equirectangular) 근사 — 도심 규모(수 km)에서만 유효. */
object SpatialMath {

    private const val EARTH_RADIUS_M: Double = 6_371_000.0

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat: Double = Math.toRadians(b.lat - a.lat)
        val dLng: Double = Math.toRadians(b.lng - a.lng)
        val h: Double = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(a.lat)) * kotlin.math.cos(Math.toRadians(b.lat)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    }

    fun pathLengthMeters(path: List<GeoPoint>): Double {
        var total: Double = 0.0
        for (i in 1 until path.size) {
            total += haversineMeters(path[i - 1], path[i])
        }
        return total
    }

    private data class Planar(val x: Double, val y: Double)

    private fun toPlanar(p: GeoPoint, originLat: Double): Planar {
        val latScale: Double = Math.toRadians(1.0) * EARTH_RADIUS_M
        val lngScale: Double = latScale * cos(Math.toRadians(originLat))
        return Planar(p.lng * lngScale, p.lat * latScale)
    }

    fun projectOntoSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val originLat: Double = p.lat
        val pp: Planar = toPlanar(p, originLat)
        val pa: Planar = toPlanar(a, originLat)
        val pb: Planar = toPlanar(b, originLat)
        val dx: Double = pb.x - pa.x
        val dy: Double = pb.y - pa.y
        val segLenSq: Double = dx * dx + dy * dy
        if (segLenSq == 0.0) {
            return SegmentProjection(t = 0.0, distanceMeters = hypot(pp.x - pa.x, pp.y - pa.y))
        }
        val tRaw: Double = ((pp.x - pa.x) * dx + (pp.y - pa.y) * dy) / segLenSq
        val t: Double = max(0.0, min(1.0, tRaw))
        val cx: Double = pa.x + t * dx
        val cy: Double = pa.y + t * dy
        return SegmentProjection(t = tRaw, distanceMeters = hypot(pp.x - cx, pp.y - cy))
    }

    fun distancePointToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double =
        projectOntoSegment(p, a, b).distanceMeters

    fun minDistanceToPathMeters(p: GeoPoint, path: List<GeoPoint>): Double {
        if (path.isEmpty()) {
            return Double.POSITIVE_INFINITY
        }
        if (path.size == 1) {
            return haversineMeters(p, path[0])
        }
        var minD: Double = Double.POSITIVE_INFINITY
        for (i in 1 until path.size) {
            val d: Double = distancePointToSegmentMeters(p, path[i - 1], path[i])
            if (d < minD) {
                minD = d
            }
        }
        return minD
    }

    fun overlapLengthMeters(base: List<GeoPoint>, other: List<GeoPoint>, meters: Double): Double {
        if (base.size < 2 || other.size < 2) {
            return 0.0
        }
        var overlap: Double = 0.0
        for (i in 1 until base.size) {
            val a: GeoPoint = base[i - 1]
            val b: GeoPoint = base[i]
            val mid: GeoPoint = GeoPoint((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
            if (minDistanceToPathMeters(mid, other) <= meters) {
                overlap += haversineMeters(a, b)
            }
        }
        return overlap
    }
}
