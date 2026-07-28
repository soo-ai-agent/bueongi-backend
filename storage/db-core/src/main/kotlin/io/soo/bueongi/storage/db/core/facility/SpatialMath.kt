package io.soo.bueongi.storage.db.core.facility

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 안전 점수/우회 계산에 쓰는 순수 지오메트리 유틸(프론트 geo.ts 의 Kotlin 이식).
 *
 * 대권거리(haversine)는 이 object 의 [haversineMeters] 가 프로젝트 전체의 단일 출처로 구현하고,
 * 점-선분 최단거리처럼 평면 기하가 필요한 계산은 기준 위도 근처 국소 평면(equirectangular) 투영으로
 * 근사한다 — 도심 규모(수 km)에서 회랑(30m) 판정과 우회 측방거리 산정에 충분하다. 외부 의존 없이 결정론적.
 */
object SpatialMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 점 사이 대권거리(m). haversine — 프로젝트 전체 거리 계산의 단일 출처. */
    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(a.lat)) * kotlin.math.cos(Math.toRadians(b.lat)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    }

    /** 경로(폴리라인) 전체 길이(m). 좌표 2개 미만이면 0. */
    fun pathLengthMeters(path: List<GeoPoint>): Double {
        var total: Double = 0.0
        for (i in 1 until path.size) {
            total += haversineMeters(path[i - 1], path[i])
        }
        return total
    }

    private data class Planar(val x: Double, val y: Double)

    /** 기준 위도 근처에서 위경도를 국소 평면(m)으로 투영. */
    private fun toPlanar(p: GeoPoint, originLat: Double): Planar {
        val latScale = Math.toRadians(1.0) * EARTH_RADIUS_M
        val lngScale = latScale * cos(Math.toRadians(originLat))
        return Planar(p.lng * lngScale, p.lat * latScale)
    }

    /**
     * 점 p 의 선분 [a,b] 위 투영 매개변수 t(클램프 전)와 선분까지의 최단거리(m)를 함께 반환한다.
     * t<0 은 a 이전, t>1 은 b 이후를 뜻한다(우회의 진행방향 중간 구간 판정에 쓰려고 클램프 전 값도 준다).
     */
    fun projectOntoSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val originLat = p.lat
        val pp = toPlanar(p, originLat)
        val pa = toPlanar(a, originLat)
        val pb = toPlanar(b, originLat)
        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        val segLenSq = dx * dx + dy * dy
        if (segLenSq == 0.0) {
            return SegmentProjection(t = 0.0, distanceMeters = hypot(pp.x - pa.x, pp.y - pa.y))
        }
        val tRaw = ((pp.x - pa.x) * dx + (pp.y - pa.y) * dy) / segLenSq
        val t = max(0.0, min(1.0, tRaw))
        val cx = pa.x + t * dx
        val cy = pa.y + t * dy
        return SegmentProjection(t = tRaw, distanceMeters = hypot(pp.x - cx, pp.y - cy))
    }

    /** 점에서 선분 [a,b]까지의 최단거리(m). */
    fun distancePointToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double =
        projectOntoSegment(p, a, b).distanceMeters

    /** 점에서 경로(폴리라인)까지의 최단거리(m). 빈/단일 좌표 경로 방어. */
    fun minDistanceToPathMeters(p: GeoPoint, path: List<GeoPoint>): Double {
        if (path.isEmpty()) {
            return Double.POSITIVE_INFINITY
        }
        if (path.size == 1) {
            return haversineMeters(p, path[0])
        }
        var minD = Double.POSITIVE_INFINITY
        for (i in 1 until path.size) {
            val d = distancePointToSegmentMeters(p, path[i - 1], path[i])
            if (d < minD) {
                minD = d
            }
        }
        return minD
    }

    /**
     * 두 경로의 겹침 길이(m) 근사. 기준 경로(base)의 각 선분 중점이 다른 경로(other) 버퍼 안에 들면
     * 그 선분 길이를 겹침으로 본다. 안심귀갓길 보너스 산정용 결정론적 근사치(프론트 overlapLengthMeters 동형).
     */
    fun overlapLengthMeters(base: List<GeoPoint>, other: List<GeoPoint>, meters: Double): Double {
        if (base.size < 2 || other.size < 2) {
            return 0.0
        }
        var overlap: Double = 0.0
        for (i in 1 until base.size) {
            val a = base[i - 1]
            val b = base[i]
            val mid = GeoPoint((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
            if (minDistanceToPathMeters(mid, other) <= meters) {
                overlap += haversineMeters(a, b)
            }
        }
        return overlap
    }
}

/**
 * 점을 선분에 투영한 결과. [t] 는 클램프 전 진행 매개변수(0 미만=선분 시작 이전, 1 초과=끝 이후),
 * [distanceMeters] 는 그 점에서 선분까지의 최단거리(m).
 */
data class SegmentProjection(val t: Double, val distanceMeters: Double)
