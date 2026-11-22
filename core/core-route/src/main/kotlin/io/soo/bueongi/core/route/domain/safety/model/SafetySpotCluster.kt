package io.soo.bueongi.core.route.domain.safety.model
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

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
    }
}
