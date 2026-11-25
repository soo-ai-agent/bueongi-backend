package io.soo.bueongi.core.route.domain.safety.model
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import kotlin.math.floor

data class SafetySpotCluster(
    val center: GeoPoint,
    val weight: Int,
) {
    companion object {
        // 평균점은 건물 안·블록 한가운데처럼 도로 밖일 수 있어, 경유지로 주면 Tmap 이 들렀다 나오는 왕복 구간(꼬임)을 만든다 — 평균점에 가장 가까운 실제 시설 위치를 대표 좌표로 쓴다.
        fun from(spots: List<SafetySpotPoint>): SafetySpotCluster {
            var sumLat = 0.0
            var sumLng = 0.0
            for (spot in spots) {
                sumLat += spot.lat
                sumLng += spot.lng
            }
            val averageCenter: GeoPoint = GeoPoint(lat = sumLat / spots.size, lng = sumLng / spots.size)
            var anchor: SafetySpotPoint = spots[0]
            var anchorDistanceMeters: Double = Double.MAX_VALUE
            for (spot in spots) {
                val distanceMeters: Double = averageCenter.distanceMetersTo(spot.toGeoPoint())
                if (distanceMeters < anchorDistanceMeters) {
                    anchorDistanceMeters = distanceMeters
                    anchor = spot
                }
            }
            return SafetySpotCluster(center = anchor.toGeoPoint(), weight = spots.size)
        }

        // 시설을 격자 칸으로 묶어 칸마다 뭉치 하나를 만들고, 많이 뭉친 순으로 낸다.
        fun groupByCell(spots: List<SafetySpotPoint>): List<SafetySpotCluster> {
            val spotsByCell: LinkedHashMap<Long, ArrayList<SafetySpotPoint>> = LinkedHashMap()
            for (spot in spots) {
                val latCell: Int = floor(spot.lat / CELL_DEG).toInt()
                val lngCell: Int = floor(spot.lng / CELL_DEG).toInt()
                val key: Long = (latCell.toLong() shl 32) xor (lngCell.toLong() and 0xffffffffL)
                spotsByCell.getOrPut(key) { ArrayList() }.add(spot)
            }

            val clusters: ArrayList<SafetySpotCluster> = ArrayList()
            for (cellSpots in spotsByCell.values) {
                val cluster: SafetySpotCluster = from(cellSpots)
                clusters.add(cluster)
            }
            val byWeightThenPosition: Comparator<SafetySpotCluster> =
                compareByDescending<SafetySpotCluster> { it.weight }
                    .thenBy { it.center.lat }
                    .thenBy { it.center.lng }
            clusters.sortWith(comparator = byWeightThenPosition)
            return clusters
        }

        private const val CELL_DEG: Double = 0.0008
    }
}
