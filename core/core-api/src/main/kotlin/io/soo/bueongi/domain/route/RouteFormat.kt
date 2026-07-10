package io.soo.bueongi.domain.route

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 경로 표시 포맷 유틸(결정론적). 거리 계산의 단일 출처는 storage 의 SpatialMath 이고,
 * 여기는 사용자에게 보여줄 문자열/분 변환만 담당한다(구 Geo 유틸의 표시 책임 분리).
 */
object RouteFormat {

    /** 미터를 표시문자열로. 1km 미만은 "850m", 이상은 "1.2km"(프론트 dist 표기 형식). */
    fun formatDistance(meters: Double): String {
        val clampedMeters = max(0.0, meters)
        return if (clampedMeters < 1000) {
            "${(clampedMeters / 10).roundToInt() * 10}m"
        } else {
            "%.1fkm".format(clampedMeters / 1000.0)
        }
    }

    /** 도보 소요(분). 평균 67m/분(≈4km/h). 최소 1분(0거리 방어). */
    fun walkMinutes(meters: Double): Int = max(1, (max(0.0, meters) / 67.0).roundToInt())
}
