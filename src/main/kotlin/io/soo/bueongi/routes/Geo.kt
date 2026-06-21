package io.soo.bueongi.routes

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 좌표·거리·표시문자열 유틸. 결정론적(외부 의존 없음) — 같은 입력엔 항상 같은 경로/시설이 나오도록.
 */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 좌표 사이 대권거리(미터). haversine. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 미터를 표시문자열로. 1km 미만은 "850m", 이상은 "1.2km"(프론트 dist 표기 형식). */
    fun formatDistance(meters: Double): String {
        val m = max(0.0, meters)
        return if (m < 1000) "${(m / 10).roundToInt() * 10}m"
        else "%.1fkm".format(m / 1000.0)
    }

    /** 도보 소요(분). 평균 67m/분(≈4km/h). 최소 1분(0거리 방어). */
    fun walkMinutes(meters: Double): Int = max(1, (max(0.0, meters) / 67.0).roundToInt())
}
