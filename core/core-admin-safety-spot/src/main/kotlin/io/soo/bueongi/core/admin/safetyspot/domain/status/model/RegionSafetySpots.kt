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
        fun from(
            code: String,
            region: RegionName,
            pointsByType: Map<SafetySpotType, List<SafetySpotPoint>>,
            budget: Int,
        ): RegionSafetySpots {
            val quotas: Map<SafetySpotType, Int> = roundRobinQuotasOf(pointsByType, budget)
            val shown: ArrayList<SafetySpotPoint> = ArrayList(budget)
            var total: Int = 0
            for ((type, points) in pointsByType) {
                shown.addAll(points.pickEvenly(quotas.getValue(type)))
                total += points.size
            }
            return RegionSafetySpots(code = code, sido = region.sido, name = region.name, total = total, spots = shown)
        }

        private fun roundRobinQuotasOf(pointsByType: Map<SafetySpotType, List<SafetySpotPoint>>, budget: Int): Map<SafetySpotType, Int> {
            val quotas: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
            for (type in pointsByType.keys) {
                quotas[type] = 0
            }
            var remaining: Int = budget
            while (remaining > 0) {
                var handedOut: Int = 0
                for ((type, points) in pointsByType) {
                    if (remaining == 0) {
                        break
                    }
                    val given: Int = quotas.getValue(type)
                    if (given >= points.size) {
                        continue
                    }
                    quotas[type] = given + 1
                    remaining--
                    handedOut++
                }
                if (handedOut == 0) {
                    break
                }
            }
            return quotas
        }
    }
}
