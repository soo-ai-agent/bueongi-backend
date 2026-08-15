package io.soo.bueongi.core.facility.domain
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SegmentProjection

import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * 최단 경로 옆으로 살짝 돌아 "안심시설이 몰린 곳"을 지나가는 경유지를 고른다.
 *
 * 개별 시설 하나하나를 경유지로 쓰면 Tmap 호출이 폭발하므로, 시설을 격자로 뭉쳐
 * **거점(anchor)** 몇 개로 줄인 뒤 그중 경로에 어울리는 것만 고른다.
 */
@Component
class SafeDetourPlanner(
    private val facilities: FacilitySpatialSource,
) {

    /**
     * 경로 회랑 안 시설을 격자로 뭉쳐 거점 목록을 만든다. 거점 = 셀 안 시설들의 무게중심, 가중치 = 시설 수.
     * 시설이 많이 몰린 셀일수록 앞에 온다.
     */
    fun extractSafeAnchors(directPath: List<GeoPoint>): List<SafeAnchor> {
        if (directPath.size < 2) {
            return emptyList()
        }
        val nearby: List<FacilityPoint> = ANCHOR_TYPES.flatMap {
            facilities.index(it).withinCorridor(directPath, ANCHOR_RADIUS_METERS)
        }
        if (nearby.isEmpty()) {
            return emptyList()
        }

        data class ClusterAccumulator(var sumLat: Double = 0.0, var sumLng: Double = 0.0, var facilityCount: Int = 0)
        val clusters: LinkedHashMap<Long, ClusterAccumulator> = LinkedHashMap<Long, ClusterAccumulator>()
        for (facility in nearby) {
            val latCell: Int = floor(facility.lat / CLUSTER_CELL_DEG).toInt()
            val lngCell: Int = floor(facility.lng / CLUSTER_CELL_DEG).toInt()
            val key: Long = GridSpatialIndex.cellKey(latCell, lngCell)
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
     * 거점 중 경유지로 쓸 만한 것을 조합해 돌려준다. 조합 하나가 Tmap 호출 한 번이라 개수를 좁게 잡는다.
     * 반환: 거점 1개짜리 조합 최대 3개 + 거점 2개짜리 조합 1개.
     */
    fun pickDetourWaypoints(
        origin: GeoPoint,
        dest: GeoPoint,
        anchors: List<SafeAnchor>,
    ): List<List<GeoPoint>> {
        if (anchors.isEmpty()) {
            return emptyList()
        }
        val maxLateralMeters: Double = origin.distanceMetersTo(dest) * DEFAULT_LATERAL_RATIO

        // 거점을 출발지→도착지 직선 위에 투영해 "얼마나 진행한 지점인지(progress)"와 "옆으로 얼마나 벗어났는지"를 본다.
        data class Placed(val anchor: SafeAnchor, val progress: Double)
        val placed: List<Placed> = anchors.mapNotNull { anchor ->
            val projection: SegmentProjection = anchor.center.projectOnto(origin, dest)
            val progress: Double = projection.t
            val lateralMeters: Double = projection.distanceMeters
            val nearEnds: Boolean = progress <= MIDDLE_MIN_PROGRESS || progress >= MIDDLE_MAX_PROGRESS
            if (nearEnds || lateralMeters > maxLateralMeters) {
                return@mapNotNull null
            }
            Placed(anchor, progress)
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
            // 2개 경유 조합은 경로 진행 순으로 정렬해야 왔던 길을 되돌아가지 않는다.
            val pair: List<GeoPoint> = byWeight.take(2).sortedBy { it.progress }.map { it.anchor.center }
            combos.add(pair)
        }
        return combos.toList()
    }

    companion object {

        /** 경로에서 이만큼 떨어진 시설까지 거점 재료로 본다(도보로 체감되는 거리). */
        const val ANCHOR_RADIUS_METERS: Double = 150.0

        /** 거점 상한. 늘리면 아래 조합 수와 Tmap 호출이 함께 늘어난다. */
        const val MAX_ANCHORS: Int = 6

        /** 거점 격자 한 칸(약 90m). 이보다 촘촘하면 같은 골목이 여러 거점으로 쪼개진다. */
        const val CLUSTER_CELL_DEG: Double = 0.0008

        /** 옆으로 벗어나도 되는 한계 = 직선거리의 이 비율. 넘으면 "돌아가는 길"로 느껴진다. */
        const val DEFAULT_LATERAL_RATIO: Double = 0.25

        /** 경유지는 경로 중간 구간에서만 고른다 — 출발·도착 근처 경유는 우회 효과가 없다. */
        const val MIDDLE_MIN_PROGRESS: Double = 0.2
        const val MIDDLE_MAX_PROGRESS: Double = 0.8

        /** 거점 1개짜리 조합 수 = Tmap 추가 호출 수. */
        const val SINGLE_ANCHOR_COMBOS: Int = 3

        val ANCHOR_TYPES: List<FacilityType> = listOf(FacilityType.CCTV, FacilityType.SAFEHOUSE, FacilityType.BELL, FacilityType.POLICE)
    }
}
