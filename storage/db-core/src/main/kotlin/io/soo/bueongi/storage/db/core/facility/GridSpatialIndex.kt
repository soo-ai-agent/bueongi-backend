package io.soo.bueongi.storage.db.core.facility

import kotlin.math.ceil
import kotlin.math.floor

class GridSpatialIndex(points: List<FacilityPoint>) {

    private val buckets: Map<Long, List<FacilityPoint>> =
        points.groupBy { cellKey(cellIndex(it.lat, CELL_DEG), cellIndex(it.lng, CELL_DEG)) }

    val size: Int = points.size

    fun withinRadius(center: GeoPoint, radiusM: Double): List<FacilityPoint> {
        if (radiusM <= 0) {
            return emptyList()
        }
        val candidates: List<FacilityPoint> = candidateCells(listOf(center), radiusM)
        return candidates.filter { center.distanceMetersTo(it.toGeoPoint()) <= radiusM }
    }

    fun withinCorridor(path: List<GeoPoint>, corridorM: Double): List<FacilityPoint> {
        if (path.isEmpty() || corridorM <= 0) {
            return emptyList()
        }
        val samples: List<GeoPoint> = densify(path)
        val candidates: List<FacilityPoint> = candidateCells(samples, corridorM)
        return candidates.filter { it.toGeoPoint().distanceMetersToPath(path) <= corridorM }
    }

    private fun densify(path: List<GeoPoint>): List<GeoPoint> {
        if (path.size < 2) {
            return path
        }
        val out: ArrayList<GeoPoint> = ArrayList<GeoPoint>()
        out.add(path[0])
        for (i in 1 until path.size) {
            val a: GeoPoint = path[i - 1]
            val b: GeoPoint = path[i]
            val spanDeg: Double = maxOf(kotlin.math.abs(b.lat - a.lat), kotlin.math.abs(b.lng - a.lng))
            val steps: Int = ceil(spanDeg / CELL_DEG).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t: Double = s.toDouble() / steps
                out.add(GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t))
            }
        }
        return out
    }

    private fun candidateCells(anchors: List<GeoPoint>, radiusM: Double): List<FacilityPoint> {
        if (buckets.isEmpty()) {
            return emptyList()
        }
        val cellSpanMinM: Double = CELL_DEG * MIN_LNG_DEG_METERS
        val ring: Int = ceil(radiusM / cellSpanMinM).toInt().coerceAtLeast(1)
        val seenCells: HashSet<Long> = HashSet<Long>()
        val result: ArrayList<FacilityPoint> = ArrayList<FacilityPoint>()
        for (anchor in anchors) {
            val cx: Int = cellIndex(anchor.lat, CELL_DEG)
            val cy: Int = cellIndex(anchor.lng, CELL_DEG)
            for (dx in -ring..ring) {
                for (dy in -ring..ring) {
                    val key: Long = cellKey(cx + dx, cy + dy)
                    if (!seenCells.add(key)) {
                        continue
                    }
                    buckets[key]?.let { result.addAll(it) }
                }
            }
        }
        return result
    }

    companion object {
        private const val CELL_DEG: Double = 0.001
        /** 경도 1°의 보수적 최소 폭(m, 한국 최북단 기준) — 이보다 키우면 후보 셀 누락. */
        private const val MIN_LNG_DEG_METERS: Double = 86_000.0

        private fun cellIndex(value: Double, cellDeg: Double): Int = floor(value / cellDeg).toInt()

        fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
    }
}
