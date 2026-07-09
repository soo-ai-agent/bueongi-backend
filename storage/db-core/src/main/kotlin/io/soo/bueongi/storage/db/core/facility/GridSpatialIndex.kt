package io.soo.bueongi.storage.db.core.facility

import kotlin.math.ceil
import kotlin.math.floor

/**
 * 그리드 버킷 기반 인메모리 공간 인덱스. 위경도를 ~약 100m 셀로 양자화한 버킷 맵으로,
 * 새 DB 인프라 없이 전국 시설을 반경/회랑 질의한다.
 *
 * 정확도: 버킷은 후보를 좁히기만 하고, 최종 포함 여부는 [SpatialMath] 의 정확한 거리(haversine/점-선분)로
 * 다시 거른다. 따라서 셀 크기는 성능 파라미터일 뿐 결과 정확도에 영향을 주지 않는다(결정론적).
 *
 * 셀 크기는 위도 0.001°(≈111m), 경도 0.001°(한국 위도에서 ≈90m)로 고정한다. 전국 단일 양자화라
 * 경도 셀의 실제 폭은 위도에 따라 달라지지만, 질의 시 반경을 셀 폭의 보수적 하한으로 환산해
 * 충분한 이웃 셀을 훑으므로 누락이 없다.
 */
class GridSpatialIndex(points: List<FacilityPoint>) {

    private val buckets: Map<Long, List<FacilityPoint>> =
        points.groupBy { cellKey(cellIndex(it.lat, CELL_DEG), cellIndex(it.lng, CELL_DEG)) }

    /** 적재된 점 개수(로그/검증용). */
    val size: Int = points.size

    /**
     * center 반경 radiusM 안의 시설. 셀 후보를 모은 뒤 정확한 대권거리로 거른다.
     */
    fun withinRadius(center: GeoPoint, radiusM: Double): List<FacilityPoint> {
        if (radiusM <= 0) return emptyList()
        val candidates = candidateCells(listOf(center), radiusM)
        return candidates.filter { SpatialMath.haversineMeters(center, it.toGeoPoint()) <= radiusM }
    }

    /**
     * 경로(좌표열) 회랑(corridorM) 안의 시설. 경로를 셀 간격으로 촘촘히 샘플링한 점들 주변 셀에서 후보를 모은 뒤
     * 점-선분 최단거리로 거른다(프론트 countWithinCorridor 와 동형 결과).
     *
     * 정점만으로 후보 셀을 잡으면 멀리 떨어진 두 정점 사이 구간(예: Tmap 직선 1구간)의 시설을 놓친다.
     * 그래서 각 선분을 셀 크기 간격으로 보간한 샘플점을 후보 앵커로 써 구간 전체를 빠짐없이 덮는다.
     */
    fun withinCorridor(path: List<GeoPoint>, corridorM: Double): List<FacilityPoint> {
        if (path.isEmpty() || corridorM <= 0) return emptyList()
        val samples = densify(path)
        val candidates = candidateCells(samples, corridorM)
        return candidates.filter { SpatialMath.minDistanceToPathMeters(it.toGeoPoint(), path) <= corridorM }
    }

    /** 각 선분을 셀 크기(CELL_DEG) 이하 간격으로 보간해 구간 전체를 덮는 샘플점 목록을 만든다. */
    private fun densify(path: List<GeoPoint>): List<GeoPoint> {
        if (path.size < 2) return path
        val out = ArrayList<GeoPoint>()
        out.add(path[0])
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            val spanDeg = maxOf(kotlin.math.abs(b.lat - a.lat), kotlin.math.abs(b.lng - a.lng))
            val steps = ceil(spanDeg / CELL_DEG).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                out.add(GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t))
            }
        }
        return out
    }

    /**
     * 질의 점들 주변 반경(m)을 덮는 셀들의 시설 후보를 중복 없이 모은다.
     * 반경을 보수적 셀 폭(경도 최소 폭)으로 환산해 충분히 넓게 훑는다.
     */
    private fun candidateCells(anchors: List<GeoPoint>, radiusM: Double): List<FacilityPoint> {
        if (buckets.isEmpty()) return emptyList()
        // 경도 1°의 최소 폭(한국 최북단 ~38.7°N)으로 보수적 환산 → 셀 반경 개수.
        val cellSpanMinM = CELL_DEG * MIN_LNG_DEG_METERS
        val ring = ceil(radiusM / cellSpanMinM).toInt().coerceAtLeast(1)
        val seenCells = HashSet<Long>()
        val result = ArrayList<FacilityPoint>()
        for (anchor in anchors) {
            val cx = cellIndex(anchor.lat, CELL_DEG)
            val cy = cellIndex(anchor.lng, CELL_DEG)
            for (dx in -ring..ring) {
                for (dy in -ring..ring) {
                    val key = cellKey(cx + dx, cy + dy)
                    if (!seenCells.add(key)) continue
                    buckets[key]?.let { result.addAll(it) }
                }
            }
        }
        return result
    }

    private companion object {
        const val CELL_DEG = 0.001
        /** 경도 1°의 보수적(최소) 폭(m). 위도가 높을수록 좁아지므로 한국 최북단 기준으로 잡아 누락 방지. */
        const val MIN_LNG_DEG_METERS = 86_000.0

        fun cellIndex(value: Double, cellDeg: Double): Int = floor(value / cellDeg).toInt()

        /** 두 셀 좌표를 64비트 키로 패킹(해시 충돌 없이). */
        fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
    }
}
