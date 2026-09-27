package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.regionCodeOrNull
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

    val regions: Set<String> = distinct.mapNotNullTo(HashSet()) { it.regionCodeOrNull() }

    // 중심점에서 radiusM 미터 안에 있는 시설을 낸다.
    fun withinRadius(center: GeoPoint, radiusM: Double): List<SafetySpotPoint> {
        if (radiusM <= 0) {
            return emptyList()
        }
        val candidates: List<SafetySpotPoint> = candidateCells(listOf(center), radiusM)
        return candidates.filter { center.distanceMetersTo(it.toGeoPoint()) <= radiusM }
    }

    // 경로 선 양옆 corridorM 미터 띠 안에 있는 시설을 낸다.
    fun withinCorridor(path: List<GeoPoint>, corridorM: Double): List<SafetySpotPoint> {
        if (path.isEmpty() || corridorM <= 0) {
            return emptyList()
        }
        val samples: List<GeoPoint> = fillPointsBetween(path)
        val candidates: List<SafetySpotPoint> = candidateCells(samples, corridorM)
        return candidates.filter { it.toGeoPoint().distanceMetersToPath(path) <= corridorM }
    }

    // 경로의 점 사이가 격자 칸보다 넓으면 중간 점을 채워, 모든 구간의 주변 칸이 검색되게 한다.
    private fun fillPointsBetween(path: List<GeoPoint>): List<GeoPoint> {
        if (path.size < 2) {
            return path
        }
        val out: ArrayList<GeoPoint> = ArrayList()
        out.add(path[0])
        for (i in 1 until path.size) {
            val from: GeoPoint = path[i - 1]
            val to: GeoPoint = path[i]
            val latSpanDeg: Double = kotlin.math.abs(to.lat - from.lat)
            val lngSpanDeg: Double = kotlin.math.abs(to.lng - from.lng)
            val spanDeg: Double = maxOf(latSpanDeg, lngSpanDeg)
            val steps: Int = ceil(spanDeg / CELL_DEG).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val progress: Double = s.toDouble() / steps
                val point: GeoPoint = from.pointTowards(to, progress)
                out.add(point)
            }
        }
        return out
    }

    // 기준점들 주변의 격자 칸에 든 시설을 모아 후보로 낸다. 칸 단위라 반경 밖도 섞여 나온다 — 호출부가 다시 거른다.
    private fun candidateCells(anchors: List<GeoPoint>, radiusM: Double): List<SafetySpotPoint> {
        if (buckets.isEmpty()) {
            return emptyList()
        }
        val cellSpanMinM: Double = CELL_DEG * MIN_LNG_DEG_METERS
        val ring: Int = ceil(radiusM / cellSpanMinM).toInt().coerceAtLeast(1)
        val seenCells: HashSet<Long> = HashSet()
        val result: ArrayList<SafetySpotPoint> = ArrayList()
        for ((lat, lng) in anchors) {
            val latCell: Int = cellIndex(lat, CELL_DEG)
            val lngCell: Int = cellIndex(lng, CELL_DEG)
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

        // 좌표가 몇 번째 칸에 드는지 낸다.
        private fun cellIndex(value: Double, cellDeg: Double): Int = floor(value / cellDeg).toInt()

        // 칸의 (세로, 가로) 번호 둘을 Long 하나에 합쳐 버킷 키로 쓴다.
        fun cellKey(latCell: Int, lngCell: Int): Long = (latCell.toLong() shl 32) xor (lngCell.toLong() and 0xffffffffL)
    }
}

// 좌표를 5자리(≈1.1m)로 반올림해 같은 지점 판정에 쓴다.
private fun roundCoord(value: Double): Long = Math.round(value * COORD_SCALE)

private const val COORD_SCALE: Double = 100_000.0
