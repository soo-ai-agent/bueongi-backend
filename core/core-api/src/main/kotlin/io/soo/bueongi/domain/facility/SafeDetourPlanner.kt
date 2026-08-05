package io.soo.bueongi.domain.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SegmentProjection
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * 안심 경유지 우회 계획(프론트 위임 스펙 safeDetour 의 Kotlin 이식).
 *
 * 직접 경로 주변의 CCTV/안심집/비상벨이 밀집한 지점을 "안심 앵커"로 뽑고, 출발→도착 진행방향의 중간 구간에서
 * 과도하게 옆으로 벗어나지 않는 앵커들로 경유지 조합 여러 개를 만든다. 이 조합을 Tmap 경유지(passList)로
 * 넘겨 더 안전한 우회 후보를 얻는다. 외부 의존 없이 결정론적.
 */
@Component
class SafeDetourPlanner(
    private val repository: FacilitySpatialSource,
) {

    /**
     * 직접 경로 주변 반경 [ANCHOR_RADIUS_METERS] 안의 CCTV/안심집/비상벨을 ~80m 그리드로 클러스터링해
     * 버킷별 시설수를 weight 로 한 클러스터 중심 앵커 목록을 만든다(weight 내림차순 상위 [MAX_ANCHORS]).
     */
    fun extractSafeAnchors(directPath: List<GeoPoint>): List<SafeAnchor> {
        if (directPath.size < 2) {
            return emptyList()
        }
        val facilities: List<FacilityPoint> = ANCHOR_TYPES.flatMap {
            repository.index(it).withinCorridor(directPath, ANCHOR_RADIUS_METERS)
        }
        if (facilities.isEmpty()) {
            return emptyList()
        }

        data class ClusterAccumulator(var sumLat: Double = 0.0, var sumLng: Double = 0.0, var facilityCount: Int = 0)
        val clusters: LinkedHashMap<Long, ClusterAccumulator> = LinkedHashMap<Long, ClusterAccumulator>()
        for (facility in facilities) {
            val cx: Int = floor(facility.lat / CLUSTER_CELL_DEG).toInt()
            val cy: Int = floor(facility.lng / CLUSTER_CELL_DEG).toInt()
            val key: Long = GridSpatialIndex.cellKey(cx, cy)
            val cluster: ClusterAccumulator = clusters.getOrPut(key) { ClusterAccumulator() }
            cluster.sumLat += facility.lat
            cluster.sumLng += facility.lng
            cluster.facilityCount += 1
        }

        return clusters.values
            .map { cluster ->
                SafeAnchor(
                    GeoPoint(cluster.sumLat / cluster.facilityCount, cluster.sumLng / cluster.facilityCount),
                    weight = cluster.facilityCount,
                )
            }
            .sortedWith(compareByDescending<SafeAnchor> { it.weight }.thenBy { it.center.lat }.thenBy { it.center.lng })
            .take(MAX_ANCHORS)
    }

    /**
     * 진행방향 중간 구간의 고가중 앵커로 경유지 조합 여러 개를 만든다.
     * - 앵커 1개짜리 조합 2~3개(weight 상위)
     * - 앵커 2개짜리 조합 1개(상위 2개를 출발거리순 정렬)
     * 과도한 측방이탈(직선상 수직거리 > 직선거리×[DEFAULT_LATERAL_RATIO])과 양 끝(중간 구간 밖)은 제외하고,
     * 각 조합의 경유지는 출발거리순(직선 투영 t 오름차순)으로 정렬한다.
     */
    fun pickDetourWaypoints(
        origin: GeoPoint,
        dest: GeoPoint,
        anchors: List<SafeAnchor>,
    ): List<List<GeoPoint>> {
        if (anchors.isEmpty()) {
            return emptyList()
        }
        val maxLateralMeters: Double = SpatialMath.haversineMeters(origin, dest) * DEFAULT_LATERAL_RATIO

        data class Placed(val anchor: SafeAnchor, val progress: Double)
        val placed: List<Placed> = anchors.mapNotNull { anchor ->
            val projection: SegmentProjection = SpatialMath.projectOntoSegment(anchor.center, origin, dest)
            val progressRaw: Double = projection.t
            val lateral: Double = projection.distanceMeters
            if (progressRaw <= MIDDLE_MIN_T || progressRaw >= MIDDLE_MAX_T) {
                return@mapNotNull null
            }
            if (lateral > maxLateralMeters) {
                return@mapNotNull null
            }
            Placed(anchor, progressRaw)
        }
        if (placed.isEmpty()) {
            return emptyList()
        }

        val byWeight: List<Placed> = placed.sortedWith(
            compareByDescending<Placed> { it.anchor.weight }.thenBy { it.progress },
        )

        val combos: LinkedHashSet<List<GeoPoint>> = LinkedHashSet<List<GeoPoint>>()
        byWeight.take(SINGLE_ANCHOR_COMBOS).forEach { combos.add(listOf(it.anchor.center)) }
        if (byWeight.size >= 2) {
            val pair: List<GeoPoint> = byWeight.take(2).sortedBy { it.progress }.map { it.anchor.center }
            combos.add(pair)
        }
        return combos.toList()
    }

    companion object {
        const val ANCHOR_RADIUS_METERS: Double = 150.0
        const val MAX_ANCHORS: Int = 6

        /** 클러스터 그리드 셀(~80m). 위도 0.0008°≈89m, 경도 0.0008°≈71m(한국). */
        const val CLUSTER_CELL_DEG: Double = 0.0008

        /** 직선 길이 대비 허용 측방이탈 비율 — maxLateralMeters = 직선거리 × 이 값. */
        const val DEFAULT_LATERAL_RATIO: Double = 0.25

        /** 진행방향 중간 구간(끝점 근처 제외) 경계. */
        const val MIDDLE_MIN_T: Double = 0.2
        const val MIDDLE_MAX_T: Double = 0.8

        /** 앵커 1개짜리 조합 최대 개수(2~3개 중 3). */
        const val SINGLE_ANCHOR_COMBOS: Int = 3

        val ANCHOR_TYPES: List<FacilityType> = listOf(FacilityType.CCTV, FacilityType.SAFEHOUSE, FacilityType.BELL, FacilityType.POLICE)
    }
}
