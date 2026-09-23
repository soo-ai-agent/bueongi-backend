package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafeAnchor
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SegmentProjection
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component
import kotlin.math.floor

@Component
class SafeDetourPlanner(
    private val safetySpots: SafetySpotSpatialSource,
) {
    fun extractSafeAnchors(directPath: List<GeoPoint>): List<SafeAnchor> {
        if (directPath.size < 2) {
            return emptyList()
        }
        val nearby: List<SafetySpotPoint> =
            ANCHOR_TYPES.flatMap {
                safetySpots.index(it).withinCorridor(directPath, ANCHOR_RADIUS_METERS)
            }
        if (nearby.isEmpty()) {
            return emptyList()
        }

        data class ClusterAccumulator(var sumLat: Double = 0.0, var sumLng: Double = 0.0, var safetySpotCount: Int = 0)
        val clusters: LinkedHashMap<Long, ClusterAccumulator> = LinkedHashMap()
        for (safetySpot in nearby) {
            val latCell: Int = floor(safetySpot.lat / CLUSTER_CELL_DEG).toInt()
            val lngCell: Int = floor(safetySpot.lng / CLUSTER_CELL_DEG).toInt()
            val key: Long = GridSpatialIndex.cellKey(latCell, lngCell)
            val cluster: ClusterAccumulator = clusters.getOrPut(key) { ClusterAccumulator() }
            cluster.sumLat += safetySpot.lat
            cluster.sumLng += safetySpot.lng
            cluster.safetySpotCount += 1
        }

        val anchors: ArrayList<SafeAnchor> = ArrayList()
        for (cluster in clusters.values) {
            anchors.add(
                SafeAnchor(
                    GeoPoint(cluster.sumLat / cluster.safetySpotCount, cluster.sumLng / cluster.safetySpotCount),
                    weight = cluster.safetySpotCount,
                ),
            )
        }
        val byDensityThenPosition: Comparator<SafeAnchor> =
            compareByDescending<SafeAnchor> { it.weight }
                .thenBy { it.center.lat }
                .thenBy { it.center.lng }
        anchors.sortWith(byDensityThenPosition)
        if (anchors.size <= MAX_ANCHORS) {
            return anchors
        }
        return anchors.subList(0, MAX_ANCHORS)
    }

    fun pickDetourWaypoints(
        origin: GeoPoint,
        destination: GeoPoint,
        anchors: List<SafeAnchor>,
    ): List<List<GeoPoint>> {
        if (anchors.isEmpty()) {
            return emptyList()
        }
        val maxLateralMeters: Double = origin.distanceMetersTo(destination) * DEFAULT_LATERAL_RATIO

        data class Placed(val anchor: SafeAnchor, val progress: Double)
        val placed: List<Placed> =
            anchors.mapNotNull { anchor ->
                val projection: SegmentProjection = anchor.center.projectOnto(origin, destination)
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

        val byWeight: List<Placed> =
            placed.sortedWith(
                compareByDescending<Placed> { it.anchor.weight }.thenBy { it.progress },
            )

        val combos: LinkedHashSet<List<GeoPoint>> = LinkedHashSet()
        val singles: List<Placed> = byWeight.take(SINGLE_ANCHOR_COMBOS)
        for (single in singles) {
            combos.add(listOf(single.anchor.center))
        }
        if (byWeight.size >= 2) {
            val topTwo: List<Placed> = byWeight.take(2)
            val inTravelOrder: List<Placed> = topTwo.sortedBy { it.progress }
            val pair: ArrayList<GeoPoint> = ArrayList()
            for (placedAnchor in inTravelOrder) {
                pair.add(placedAnchor.anchor.center)
            }
            combos.add(pair)
        }
        return combos.toList()
    }

    companion object {
        const val ANCHOR_RADIUS_METERS: Double = 150.0

        const val MAX_ANCHORS: Int = 6

        const val CLUSTER_CELL_DEG: Double = 0.0008

        const val DEFAULT_LATERAL_RATIO: Double = 0.25

        const val MIDDLE_MIN_PROGRESS: Double = 0.2
        const val MIDDLE_MAX_PROGRESS: Double = 0.8

        const val SINGLE_ANCHOR_COMBOS: Int = 3

        val ANCHOR_TYPES: List<SafetySpotType> =
            listOf(SafetySpotType.CCTV, SafetySpotType.SAFEHOUSE, SafetySpotType.BELL, SafetySpotType.POLICE)
    }
}
