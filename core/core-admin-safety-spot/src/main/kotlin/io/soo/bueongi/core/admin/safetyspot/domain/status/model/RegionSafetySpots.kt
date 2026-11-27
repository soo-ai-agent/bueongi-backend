package io.soo.bueongi.core.admin.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.support.util.pickEvenly

data class RegionSafetySpots(
    val code: String,
    val sido: String,
    val name: String,
    val total: Int,
    val spots: List<SafetySpotPoint>,
) {
    companion object {
        // 지도에는 maxCount 개까지만 보여 준다. total 은 자르기 전의 전체 수다.
        fun from(
            code: String,
            region: RegionName,
            pointsByType: Map<SafetySpotType, List<SafetySpotPoint>>,
            maxCount: Int,
        ): RegionSafetySpots {
            val showCountByType: Map<SafetySpotType, Int> = shareByType(pointsByType, maxCount)
            val shownSpots: ArrayList<SafetySpotPoint> = ArrayList(maxCount)
            var total: Int = 0
            for ((type, points) in pointsByType) {
                val showCount: Int = showCountByType.getValue(type)
                shownSpots.addAll(points.pickEvenly(showCount))
                total += points.size
            }
            return RegionSafetySpots(code = code, sido = region.sido, name = region.name, total = total, spots = shownSpots)
        }

        // 보여 줄 자리를 종류마다 돌아가며 하나씩 나눠 준다 — 수가 많은 종류가 자리를 다 차지하지 않게 하려는 것이다.
        // 자리가 다 차거나, 한 바퀴를 돌아도 더 줄 종류가 없으면 멈춘다.
        private fun shareByType(pointsByType: Map<SafetySpotType, List<SafetySpotPoint>>, maxCount: Int): Map<SafetySpotType, Int> {
            val showCountByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
            for (type in pointsByType.keys) {
                showCountByType[type] = 0
            }
            var leftCount: Int = maxCount
            var gaveInThisRound: Boolean = true
            while (leftCount > 0 && gaveInThisRound) {
                gaveInThisRound = false
                for ((type, points) in pointsByType) {
                    val showCount: Int = showCountByType.getValue(type)
                    if (leftCount > 0 && showCount < points.size) {
                        showCountByType[type] = showCount + 1
                        leftCount--
                        gaveInThisRound = true
                    }
                }
            }
            return showCountByType
        }
    }
}
