package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.route.domain.safety.model.SafeAnchor
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.model.SegmentProjection

import org.springframework.stereotype.Component
import kotlin.math.floor

/** 시설 하나하나를 경유지로 쓰면 Tmap 호출이 폭발한다 — 격자로 뭉친 거점 몇 개로 줄여 쓴다. */
@Component
class SafeDetourPlanner(
    private val safetySpots: SafetySpotSpatialSource,
) {

    fun extractSafeAnchors(directPath: List<GeoPoint>): List<SafeAnchor> {
        if (directPath.size < 2) {
            return emptyList()
        }
        val nearby: List<SafetySpotPoint> = ANCHOR_TYPES.flatMap {
            safetySpots.index(it).withinCorridor(directPath, ANCHOR_RADIUS_METERS)
        }
        if (nearby.isEmpty()) {
            return emptyList()
        }

        data class ClusterAccumulator(var sumLat: Double = 0.0, var sumLng: Double = 0.0, var safetySpotCount: Int = 0)
        val clusters: LinkedHashMap<Long, ClusterAccumulator> = LinkedHashMap<Long, ClusterAccumulator>()
        for (safetySpot in nearby) {
            val latCell: Int = floor(safetySpot.lat / CLUSTER_CELL_DEG).toInt()
            val lngCell: Int = floor(safetySpot.lng / CLUSTER_CELL_DEG).toInt()
            val key: Long = GridSpatialIndex.cellKey(latCell, lngCell)
            val cluster: ClusterAccumulator = clusters.getOrPut(key) { ClusterAccumulator() }
            cluster.sumLat += safetySpot.lat
            cluster.sumLng += safetySpot.lng
            cluster.safetySpotCount += 1
        }

        val anchors: ArrayList<SafeAnchor> = ArrayList<SafeAnchor>()
        for (cluster in clusters.values) {
            anchors.add(
                SafeAnchor(
                    GeoPoint(cluster.sumLat / cluster.safetySpotCount, cluster.sumLng / cluster.safetySpotCount),
                    weight = cluster.safetySpotCount,
                ),
            )
        }
        // 같은 수면 좌표 순으로 갈라 같은 입력에 같은 결과가 나오게 한다.
        val byDensityThenPosition: Comparator<SafeAnchor> = compareByDescending<SafeAnchor> { it.weight }
            .thenBy { it.center.lat }
            .thenBy { it.center.lng }
        anchors.sortWith(byDensityThenPosition)
        if (anchors.size <= MAX_ANCHORS) {
            return anchors
        }
        return anchors.subList(0, MAX_ANCHORS)
    }

    /** 조합 하나가 Tmap 호출 한 번이라 개수를 좁게 잡는다. */
    fun pickDetourWaypoints(
        origin: GeoPoint,
        dest: GeoPoint,
        anchors: List<SafeAnchor>,
    ): List<List<GeoPoint>> {
        if (anchors.isEmpty()) {
            return emptyList()
        }
        val maxLateralMeters: Double = origin.distanceMetersTo(dest) * DEFAULT_LATERAL_RATIO

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
        val singles: List<Placed> = byWeight.take(SINGLE_ANCHOR_COMBOS)
        for (single in singles) {
            combos.add(listOf(single.anchor.center))
        }
        if (byWeight.size >= 2) {
            // 2개 경유 조합은 경로 진행 순으로 정렬해야 왔던 길을 되돌아가지 않는다.
            val topTwo: List<Placed> = byWeight.take(2)
            val inTravelOrder: List<Placed> = topTwo.sortedBy { it.progress }
            val pair: ArrayList<GeoPoint> = ArrayList<GeoPoint>()
            for (placedAnchor in inTravelOrder) {
                pair.add(placedAnchor.anchor.center)
            }
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

        val ANCHOR_TYPES: List<SafetySpotType> = listOf(SafetySpotType.CCTV, SafetySpotType.SAFEHOUSE, SafetySpotType.BELL, SafetySpotType.POLICE)
    }
}
