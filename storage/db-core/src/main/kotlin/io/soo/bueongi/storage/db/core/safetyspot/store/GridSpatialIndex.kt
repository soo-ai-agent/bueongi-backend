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

    /**
     * 같은 지점을 한 번만 센다.
     *
     * 원천은 한 지점에 시설이 여러 개면(CCTV 카메라 여러 대, 보안등 여러 등) 행을 나눠 준다.
     * 그대로 두면 지도에 마커가 겹쳐 찍히고 **밀도 점수가 시설 대수만큼 부풀어** 안전 점수가 틀어진다.
     * 안전 점수가 재는 것은 "이 길에 조명이 몇 군데 있나"이지 "전구가 몇 개인가"가 아니다.
     *
     * 좌표 5자리(≈1.1m)로 같음을 판정한다 — 원천 좌표 정밀도가 그 아래로는 의미가 없다.
     */
    private val distinct: List<SafetySpotPoint> =
        points.distinctBy { Pair(roundCoord(it.lat), roundCoord(it.lng)) }

    private val buckets: Map<Long, List<SafetySpotPoint>> =
        distinct.groupBy { cellKey(cellIndex(it.lat, CELL_DEG), cellIndex(it.lng, CELL_DEG)) }

    val size: Int = distinct.size

    /** 병합용 원본(중복 제거된 것) — 지역 데이터를 더할 때 기존 것과 합치려면 필요하다. */
    val all: List<SafetySpotPoint> = distinct

    /**
     * 이미 데이터를 가진 자치단체 코드.
     * 지역 지연 적재가 "이 지역을 또 받아야 하나"를 외부 호출 없이 여기서 판단한다.
     */
    val regions: Set<String> = distinct.mapNotNullTo(HashSet()) { it.sigungu }

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
        val out: ArrayList<GeoPoint> = ArrayList()
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

/** 좌표 5자리 ≈ 1.1m. 이보다 가까운 두 점은 같은 지점으로 본다. */
private fun roundCoord(value: Double): Long =
    Math.round(value * COORD_SCALE)

private const val COORD_SCALE: Double = 100_000.0
