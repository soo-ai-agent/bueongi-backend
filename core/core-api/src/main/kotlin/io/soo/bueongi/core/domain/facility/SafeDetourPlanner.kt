package io.soo.bueongi.core.domain.facility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SegmentProjection
import io.soo.bueongi.storage.db.core.facility.SpatialMath

import org.springframework.stereotype.Component
import kotlin.math.floor

@Component
class SafeDetourPlanner(
    private val repository: FacilitySpatialSource,
) {

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

        const val CLUSTER_CELL_DEG: Double = 0.0008

        const val DEFAULT_LATERAL_RATIO: Double = 0.25

        const val MIDDLE_MIN_T: Double = 0.2
        const val MIDDLE_MAX_T: Double = 0.8

        const val SINGLE_ANCHOR_COMBOS: Int = 3

        val ANCHOR_TYPES: List<FacilityType> = listOf(FacilityType.CCTV, FacilityType.SAFEHOUSE, FacilityType.BELL, FacilityType.POLICE)
    }
}
