package io.soo.bueongi.core.route.domain.safety.service
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.model.SegmentProjection
import io.soo.bueongi.storage.db.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Component
import kotlin.math.floor

@Component
class SafetySpotWaypointPlanner(
    private val safetySpots: SafetySpotSpatialSource,
) {
    // 경로 양옆을 훑어 안심시설이 뭉친 자리를 찾고, 뭉치마다 대표 좌표와 시설 수를 낸다. 많이 뭉친 순으로 최대 6개.
    fun clusterSafetySpots(routePath: List<GeoPoint>): List<SafetySpotCluster> {
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
            // 평균점은 건물 안·블록 한가운데처럼 도로 밖일 수 있어, 경유지로 주면 Tmap 이 들렀다 나오는
            // 왕복 구간(꼬임)을 만든다 — 평균점에 가장 가까운 실제 시설 위치를 대표 좌표로 쓴다.
            val averageCenter: GeoPoint = GeoPoint(sumLat / spots.size, sumLng / spots.size)
            var anchor: SafetySpotPoint = spots[0]
            var anchorDistanceM: Double = Double.MAX_VALUE
            for (spot in spots) {
                val distanceM: Double = averageCenter.distanceMetersTo(spot.toGeoPoint())
                if (distanceM < anchorDistanceM) {
                    anchorDistanceM = distanceM
                    anchor = spot
                }
            }
            val cluster: SafetySpotCluster = SafetySpotCluster(anchor.toGeoPoint(), weight = spots.size)
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

    // 시설 뭉치 중 경로 중간쯤(진행률 20~80%)에 있고 옆으로 크게 벗어나지 않은 것만 남긴 뒤, 시설 많은 순 상위 3곳 각각(단일)과 상위 2곳 묶음(쌍)으로 우회 경로용 경유지 계획을 만든다.
    fun pickWaypoints(origin: GeoPoint, destination: GeoPoint, clusters: List<SafetySpotCluster>): List<List<GeoPoint>> {
        if (clusters.isEmpty()) {
            return emptyList()
        }
        val placed: List<PlacedCluster> = filterMiddleClusters(origin, destination, clusters)
        if (placed.isEmpty()) {
            return emptyList()
        }
        return buildPlans(placed)
    }

    // 경로 직선에 투영해 끝점 근처(진행률 20% 미만·80% 초과)이거나 옆이탈이 직선거리의 25%를 넘는 뭉치를 버린다.
    private fun filterMiddleClusters(
        origin: GeoPoint,
        destination: GeoPoint,
        clusters: List<SafetySpotCluster>,
    ): List<PlacedCluster> {
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
        return placed
    }

    // 시설 많은 순 상위 3곳 각각(단일)과 상위 2곳을 이동 순서로 묶은 쌍 하나로 경유지 계획을 만든다.
    private fun buildPlans(placed: List<PlacedCluster>): List<List<GeoPoint>> {
        val byWeightThenProgress: Comparator<PlacedCluster> =
            compareByDescending<PlacedCluster> { it.cluster.weight }
                .thenBy { it.progress }
        val byWeight: List<PlacedCluster> = placed.sortedWith(byWeightThenProgress)

        val plans: LinkedHashSet<List<GeoPoint>> = LinkedHashSet()
        val singles: List<PlacedCluster> = byWeight.take(SINGLE_CLUSTER_PLANS)
        for (single in singles) {
            plans.add(listOf(single.cluster.center))
        }
        if (byWeight.size >= 2) {
            val topTwo: List<PlacedCluster> = byWeight.take(2)
            val inTravelOrder: List<PlacedCluster> = topTwo.sortedBy { it.progress }
            val pair: ArrayList<GeoPoint> = ArrayList()
            for (placedCluster in inTravelOrder) {
                pair.add(placedCluster.cluster.center)
            }
            plans.add(pair)
        }
        return plans.toList()
    }

    // 경로 직선 위에 투영해 본 덩어리 — 어디쯤(progress) 있는지를 같이 든다.
    private data class PlacedCluster(
        val cluster: SafetySpotCluster,
        val progress: Double,
    )

    companion object {
        const val CLUSTER_RADIUS_METERS: Double = 150.0

        // 뭉치는 우회 경로의 경유지 후보다. 실제로 쓰는 것은 시설이 많은 순으로 3곳과 상위 2곳 묶음 하나뿐이라, 뒤 단계에서 위치로 걸러질 몫을 더해 6개면 넉넉하다. (6이라는 수 자체는 실측 근거가 없는 감각값이다.)
        const val MAX_CLUSTERS: Int = 6
        const val CLUSTER_CELL_DEG: Double = 0.0008
        const val DEFAULT_LATERAL_RATIO: Double = 0.25
        const val MIDDLE_MIN_PROGRESS: Double = 0.2
        const val MIDDLE_MAX_PROGRESS: Double = 0.8
        const val SINGLE_CLUSTER_PLANS: Int = 3

        val CLUSTER_SPOT_TYPES: List<SafetySpotType> =
            listOf(SafetySpotType.CCTV, SafetySpotType.SAFEHOUSE, SafetySpotType.BELL, SafetySpotType.POLICE)
    }
}
