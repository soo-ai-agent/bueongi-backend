package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 전국 수십만 건을 매번 훑을 수 없어 격자 칸으로 후보를 먼저 줄인다.
 * 칸을 넉넉히 잡으므로 후보에는 반경 밖도 섞여 있고, 정확한 걸러내기는 조회 메서드가 한다.
 */
class GridSpatialIndex(points: List<SafetySpotPoint>) {

    private val buckets: Map<Long, List<SafetySpotPoint>> =
        points.groupBy { cellKey(cellIndex(it.lat, CELL_DEG), cellIndex(it.lng, CELL_DEG)) }

    val size: Int = points.size

    fun withinRadius(center: GeoPoint, radiusM: Double): List<SafetySpotPoint> {
        if (radiusM <= 0) {
            return emptyList()
        }
        val candidates: List<SafetySpotPoint> = candidateCells(listOf(center), radiusM)
        return candidates.filter { center.distanceMetersTo(it.toGeoPoint()) <= radiusM }
    }

    fun withinCorridor(path: List<GeoPoint>, corridorM: Double): List<SafetySpotPoint> {
        if (path.isEmpty() || corridorM <= 0) {
            return emptyList()
        }
        val samples: List<GeoPoint> = densify(path)
        val candidates: List<SafetySpotPoint> = candidateCells(samples, corridorM)
        return candidates.filter { it.toGeoPoint().distanceMetersToPath(path) <= corridorM }
    }

    /**
     * 경로 꼭짓점만으로는 긴 직선 구간의 중간 칸을 놓친다.
     * 한 칸 간격으로 점을 채워 넣어 지나가는 칸이 빠지지 않게 한다.
     */
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

    /** 반경 밖도 섞여 있어 호출부가 다시 거른다. */
    private fun candidateCells(anchors: List<GeoPoint>, radiusM: Double): List<SafetySpotPoint> {
        if (buckets.isEmpty()) {
            return emptyList()
        }
        val cellSpanMinM: Double = CELL_DEG * MIN_LNG_DEG_METERS
        val ring: Int = ceil(radiusM / cellSpanMinM).toInt().coerceAtLeast(1)
        val seenCells: HashSet<Long> = HashSet<Long>()
        val result: ArrayList<SafetySpotPoint> = ArrayList<SafetySpotPoint>()
        for (anchor in anchors) {
            val latCell: Int = cellIndex(anchor.lat, CELL_DEG)
            val lngCell: Int = cellIndex(anchor.lng, CELL_DEG)
            for (latStep in -ring..ring) {
                for (lngStep in -ring..ring) {
                    val key: Long = cellKey(latCell + latStep, lngCell + lngStep)
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
        /** 격자 한 칸(약 100m). 작을수록 후보가 줄지만 칸 수와 ring 반복이 늘어난다. */
        private const val CELL_DEG: Double = 0.001
        /** 경도 1°의 보수적 최소 폭(m, 한국 최북단 기준) — 이보다 키우면 후보 셀 누락. */
        private const val MIN_LNG_DEG_METERS: Double = 86_000.0

        private fun cellIndex(value: Double, cellDeg: Double): Int =
            floor(value / cellDeg).toInt()

        /** 두 칸 번호를 Long 하나로 합친다(상위 32비트=위도 칸, 하위 32비트=경도 칸). */
        fun cellKey(latCell: Int, lngCell: Int): Long =
            (latCell.toLong() shl 32) xor (lngCell.toLong() and 0xffffffffL)
    }
}
