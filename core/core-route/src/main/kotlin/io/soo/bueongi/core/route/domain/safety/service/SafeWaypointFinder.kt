package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SegmentProjection
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component
import kotlin.math.floor

@Component
class SafeWaypointFinder(
    private val safetySpots: SafetySpotSpatialSource,
) {
    // 경로 양옆을 훑어 안심시설이 뭉친 자리를 찾고, 뭉치마다 중심점과 시설 수를 낸다. 많이 뭉친 순으로 최대 6개.
    fun findSafetySpotClusters(routePath: List<GeoPoint>): List<SafetySpotCluster> {
        if (routePath.size < 2) {
            return emptyList()
        }
        val spotsNearRoute: ArrayList<SafetySpotPoint> = ArrayList()
        for (spotType in CLUSTER_SPOT_TYPES) {
            val spots: List<SafetySpotPoint> = safetySpots.index(spotType).withinCorridor(routePath, CLUSTER_RADIUS_METERS)
            spotsNearRoute.addAll(spots)
        }
        if (spotsNearRoute.isEmpty()) {
            return emptyList()
        }

        val spotsByCell: LinkedHashMap<Long, ArrayList<SafetySpotPoint>> = LinkedHashMap()
        for (safetySpot in spotsNearRoute) {
            val latCell: Int = floor(safetySpot.lat / CLUSTER_CELL_DEG).toInt()
            val lngCell: Int = floor(safetySpot.lng / CLUSTER_CELL_DEG).toInt()
            val key: Long = GridSpatialIndex.cellKey(latCell, lngCell)
            spotsByCell.getOrPut(key) { ArrayList() }.add(safetySpot)
        }

        val clusters: ArrayList<SafetySpotCluster> = ArrayList()
        for (spots in spotsByCell.values) {
            var sumLat = 0.0
            var sumLng = 0.0
            for (spot in spots) {
                sumLat += spot.lat
                sumLng += spot.lng
            }
            val center = GeoPoint(sumLat / spots.size, sumLng / spots.size)
            val cluster = SafetySpotCluster(center, weight = spots.size)
            clusters.add(cluster)
        }
        val byDensityThenPosition: Comparator<SafetySpotCluster> =
            compareByDescending<SafetySpotCluster> { it.weight }
                .thenBy { it.center.lat }
                .thenBy { it.center.lng }
        clusters.sortWith(byDensityThenPosition)
        if (clusters.size <= MAX_CLUSTERS) {
            return clusters
        }
        return clusters.subList(0, MAX_CLUSTERS)
    }

    fun pickWaypoints(origin: GeoPoint, destination: GeoPoint, clusters: List<SafetySpotCluster>): List<List<GeoPoint>> {
        if (clusters.isEmpty()) {
            return emptyList()
        }
        val maxLateralMeters: Double = origin.distanceMetersTo(destination) * DEFAULT_LATERAL_RATIO
        val placed: ArrayList<PlacedCluster> = ArrayList()
        for (cluster in clusters) {
            val projection: SegmentProjection = cluster.center.projectOnto(origin, destination)
            val progress: Double = projection.t
            val lateralMeters: Double = projection.distanceMeters
            val nearEnds: Boolean = progress <= MIDDLE_MIN_PROGRESS || progress >= MIDDLE_MAX_PROGRESS
            if (nearEnds) {
                continue
            }
            if (lateralMeters > maxLateralMeters) {
                continue
            }
            placed.add(PlacedCluster(cluster, progress))
        }
        if (placed.isEmpty()) {
            return emptyList()
        }

        val byWeightThenProgress: Comparator<PlacedCluster> = compareByDescending<PlacedCluster> { it.cluster.weight }.thenBy { it.progress }
        val byWeight: List<PlacedCluster> = placed.sortedWith(byWeightThenProgress)

        val combos: LinkedHashSet<List<GeoPoint>> = LinkedHashSet()
        val singles: List<PlacedCluster> = byWeight.take(SINGLE_CLUSTER_COMBOS)
        for (single in singles) {
            combos.add(listOf(single.cluster.center))
        }
        if (byWeight.size >= 2) {
            val topTwo: List<PlacedCluster> = byWeight.take(2)
            val inTravelOrder: List<PlacedCluster> = topTwo.sortedBy { it.progress }
            val pair: ArrayList<GeoPoint> = ArrayList()
            for (placedCluster in inTravelOrder) {
                pair.add(placedCluster.cluster.center)
            }
            combos.add(pair)
        }
        return combos.toList()
    }

    // 경로 직선 위에 투영해 본 덩어리 — 어디쯤(progress) 있는지를 같이 든다.
    private data class PlacedCluster(val cluster: SafetySpotCluster, val progress: Double)

    companion object {
        const val CLUSTER_RADIUS_METERS: Double = 150.0

        const val MAX_CLUSTERS: Int = 6

        const val CLUSTER_CELL_DEG: Double = 0.0008

        const val DEFAULT_LATERAL_RATIO: Double = 0.25

        const val MIDDLE_MIN_PROGRESS: Double = 0.2
        const val MIDDLE_MAX_PROGRESS: Double = 0.8

        const val SINGLE_CLUSTER_COMBOS: Int = 3

        val CLUSTER_SPOT_TYPES: List<SafetySpotType> =
            listOf(SafetySpotType.CCTV, SafetySpotType.SAFEHOUSE, SafetySpotType.BELL, SafetySpotType.POLICE)
    }
}
