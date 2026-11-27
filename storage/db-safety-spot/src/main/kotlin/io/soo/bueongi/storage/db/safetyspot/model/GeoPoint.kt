package io.soo.bueongi.storage.db.safetyspot.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val lat: Double,
    val lng: Double,
) {
    // 지구를 공으로 보고 두 좌표 사이의 거리를 구한다(하버사인 공식).
    fun distanceMetersTo(other: GeoPoint): Double {
        val latDiff: Double = Math.toRadians(other.lat - lat)
        val lngDiff: Double = Math.toRadians(other.lng - lng)
        val haversine: Double =
            sin(latDiff / 2) * sin(latDiff / 2) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) *
                sin(lngDiff / 2) * sin(lngDiff / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    // ratio 가 0 이면 이 점, 1 이면 other, 0.5 면 둘의 한가운데다.
    fun pointBetween(other: GeoPoint, ratio: Double): GeoPoint {
        return GeoPoint(lat = lat + (other.lat - lat) * ratio, lng = lng + (other.lng - lng) * ratio)
    }

    // 이 점이 선분(start~end) 옆 어디에 있는지 구한다.
    // 좁은 범위를 평면으로 보고 계산하므로 도심 규모(수 km)에서만 맞다.
    fun positionOnLine(start: GeoPoint, end: GeoPoint): LinePosition {
        val self: FlatPoint = toFlat(lat)
        val flatStart: FlatPoint = start.toFlat(lat)
        val flatEnd: FlatPoint = end.toFlat(lat)
        val lineX: Double = flatEnd.x - flatStart.x
        val lineY: Double = flatEnd.y - flatStart.y
        val lineLengthSquared: Double = lineX * lineX + lineY * lineY
        if (lineLengthSquared == 0.0) {
            val distanceMeters: Double = hypot(self.x - flatStart.x, self.y - flatStart.y)
            return LinePosition(progress = 0.0, distanceMeters = distanceMeters)
        }

        val progress: Double = ((self.x - flatStart.x) * lineX + (self.y - flatStart.y) * lineY) / lineLengthSquared
        // 선분 밖으로 벗어난 자리는 가까운 끝점으로 당겨서 거리를 잰다.
        val progressOnLine: Double = max(0.0, min(1.0, progress))
        val nearestX: Double = flatStart.x + progressOnLine * lineX
        val nearestY: Double = flatStart.y + progressOnLine * lineY
        return LinePosition(progress = progress, distanceMeters = hypot(self.x - nearestX, self.y - nearestY))
    }

    fun distanceMetersToPath(path: List<GeoPoint>): Double {
        if (path.isEmpty()) {
            return Double.POSITIVE_INFINITY
        }
        if (path.size == 1) {
            return distanceMetersTo(path[0])
        }
        var nearestMeters: Double = Double.POSITIVE_INFINITY
        for (index in 1 until path.size) {
            val distanceMeters: Double = positionOnLine(path[index - 1], path[index]).distanceMeters
            if (distanceMeters < nearestMeters) {
                nearestMeters = distanceMeters
            }
        }
        return nearestMeters
    }

    // 위경도를 미터 단위 평면 좌표로 바꾼다. 경도 1도의 길이는 위도마다 달라서 기준 위도(baseLat)로 맞춘다.
    private fun toFlat(baseLat: Double): FlatPoint {
        val metersPerLatDegree: Double = Math.toRadians(1.0) * EARTH_RADIUS_M
        val metersPerLngDegree: Double = metersPerLatDegree * cos(Math.toRadians(baseLat))
        return FlatPoint(x = lng * metersPerLngDegree, y = lat * metersPerLatDegree)
    }

    companion object {
        private const val EARTH_RADIUS_M: Double = 6_371_000.0

        fun isValidLat(lat: Double): Boolean {
            return lat.isFinite() && lat in -90.0..90.0
        }

        fun isValidLng(lng: Double): Boolean {
            return lng.isFinite() && lng in -180.0..180.0
        }

        fun orNull(lat: Double?, lng: Double?): GeoPoint? {
            if (lat == null || lng == null) {
                return null
            }
            if (!isValidLat(lat) || !isValidLng(lng)) {
                return null
            }
            return GeoPoint(lat, lng)
        }
    }
}

fun List<GeoPoint>.pathLengthMeters(): Double {
    var totalMeters: Double = 0.0
    for (index in 1 until size) {
        totalMeters += this[index - 1].distanceMetersTo(this[index])
    }
    return totalMeters
}

// 경로를 따라 gapMeters 마다 한 점씩, 처음과 끝을 넣어 limit 개까지 고른다.
// 경로가 길어 limit 개로 모자라면 간격을 넓혀 경로 전체에 고르게 편다.
fun List<GeoPoint>.pickPointsAlong(gapMeters: Double, limit: Int): List<GeoPoint> {
    if (isEmpty()) {
        return emptyList()
    }
    if (size < 2 || limit <= 2) {
        return listOf(first(), last())
    }
    val totalMeters: Double = pathLengthMeters()
    if (totalMeters <= 0) {
        return listOf(first(), last())
    }
    val usedGapMeters: Double = max(gapMeters, totalMeters / (limit - 1))
    val picked: ArrayList<GeoPoint> = ArrayList()
    picked.add(first())
    var walkedMeters: Double = 0.0
    var nextPickAtMeters: Double = usedGapMeters
    for (index in 1 until size) {
        val from: GeoPoint = this[index - 1]
        val to: GeoPoint = this[index]
        val sectionMeters: Double = from.distanceMetersTo(to)
        while (nextPickAtMeters < walkedMeters + sectionMeters && picked.size < limit - 1) {
            val ratio: Double = (nextPickAtMeters - walkedMeters) / sectionMeters
            picked.add(from.pointBetween(to, ratio))
            nextPickAtMeters += usedGapMeters
        }
        walkedMeters += sectionMeters
    }
    picked.add(last())
    return picked
}

// 이 경로 가운데 other 경로에서 withinMeters 안으로 붙어 가는 구간의 길이를 더한다.
fun List<GeoPoint>.overlapMetersWith(other: List<GeoPoint>, withinMeters: Double): Double {
    if (size < 2 || other.size < 2) {
        return 0.0
    }
    var overlapMeters: Double = 0.0
    for (index in 1 until size) {
        val from: GeoPoint = this[index - 1]
        val to: GeoPoint = this[index]
        val middle: GeoPoint = GeoPoint((from.lat + to.lat) / 2, (from.lng + to.lng) / 2)
        if (middle.distanceMetersToPath(other) <= withinMeters) {
            overlapMeters += from.distanceMetersTo(to)
        }
    }
    return overlapMeters
}
