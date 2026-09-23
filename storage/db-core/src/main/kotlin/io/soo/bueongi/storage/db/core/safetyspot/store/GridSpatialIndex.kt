package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import kotlin.math.ceil
import kotlin.math.floor

class GridSpatialIndex(points: List<SafetySpotPoint>) {
    // 한 지점의 시설 여러 대를 원천이 행으로 나눠 주므로, 좌표 5자리(≈1.1m)가 같으면 한 지점으로 센다.
    private val distinct: List<SafetySpotPoint> =
        points.distinctBy { Pair(roundCoord(it.lat), roundCoord(it.lng)) }

    private val buckets: Map<Long, List<SafetySpotPoint>> =
        distinct.groupBy { cellKey(cellIndex(it.lat, CELL_DEG), cellIndex(it.lng, CELL_DEG)) }

    val size: Int = distinct.size

    val all: List<SafetySpotPoint> = distinct

    val regions: Set<String> = distinct.mapNotNullTo(HashSet()) { it.sigungu }

    fun withinRadius(
        center: GeoPoint,
        radiusM: Double,
    ): List<SafetySpotPoint> {
        if (radiusM <= 0) {
            return emptyList()
        }
        val candidates: List<SafetySpotPoint> = candidateCells(listOf(center), radiusM)
        return candidates.filter { center.distanceMetersTo(it.toGeoPoint()) <= radiusM }
    }

    fun withinCorridor(
        path: List<GeoPoint>,
        corridorM: Double,
    ): List<SafetySpotPoint> {
        if (path.isEmpty() || corridorM <= 0) {
            return emptyList()
        }
        val samples: List<GeoPoint> = densify(path)
        val candidates: List<SafetySpotPoint> = candidateCells(samples, corridorM)
        return candidates.filter { it.toGeoPoint().distanceMetersToPath(path) <= corridorM }
    }

    private fun densify(path: List<GeoPoint>): List<GeoPoint> {
        if (path.size < 2) {
            return path
        }
        val out: ArrayList<GeoPoint> = ArrayList()
        out.add(path[0])
        for (i in 1 until path.size) {
            val a: GeoPoint = path[i - 1]
            val b: GeoPoint = path[i]
            val spanDeg: Double = maxOf(kotlin.math.abs(b.lat - a.lat), kotlin.math.abs(b.lng - a.lng))
            val steps: Int = ceil(spanDeg / CELL_DEG).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                out.add(a.pointTowards(b, s.toDouble() / steps))
            }
        }
        return out
    }

    // 격자 칸 단위라 반경 밖도 섞여 나온다 — 호출부가 다시 거른다.
    private fun candidateCells(
        anchors: List<GeoPoint>,
        radiusM: Double,
    ): List<SafetySpotPoint> {
        if (buckets.isEmpty()) {
            return emptyList()
        }
        val cellSpanMinM: Double = CELL_DEG * MIN_LNG_DEG_METERS
        val ring: Int = ceil(radiusM / cellSpanMinM).toInt().coerceAtLeast(1)
        val seenCells: HashSet<Long> = HashSet()
        val result: ArrayList<SafetySpotPoint> = ArrayList()
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
        private const val CELL_DEG: Double = 0.001

        // 한국 최북단 기준 경도 1°의 최소 폭 — 이보다 키우면 후보 셀이 누락된다.
        private const val MIN_LNG_DEG_METERS: Double = 86_000.0

        private fun cellIndex(
            value: Double,
            cellDeg: Double,
        ): Int = floor(value / cellDeg).toInt()

        fun cellKey(
            latCell: Int,
            lngCell: Int,
        ): Long = (latCell.toLong() shl 32) xor (lngCell.toLong() and 0xffffffffL)
    }
}

private fun roundCoord(value: Double): Long = Math.round(value * COORD_SCALE)

private const val COORD_SCALE: Double = 100_000.0
