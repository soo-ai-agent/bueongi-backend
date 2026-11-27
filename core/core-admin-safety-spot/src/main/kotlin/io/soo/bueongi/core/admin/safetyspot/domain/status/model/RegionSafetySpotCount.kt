package io.soo.bueongi.core.admin.safetyspot.domain.status.model

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.RegionSpotTally

data class RegionSafetySpotCount(
    val code: String,
    val sido: String,
    val name: String,
    val countsByType: Map<SafetySpotType, Int>,
    val fileCount: Int,
    val apiCount: Int,
    /** 지역을 모르거나 국내 좌표가 하나도 없으면 null — 지도에 중심을 찍지 않는다. */
    val center: GeoPoint?,
) {
    val total: Int
        get() {
            return fileCount + apiCount
        }

    companion object {
        // 지역 코드가 없는 시설을 모아 보여 줄 때 쓰는 코드. 화면이 이 값을 그대로 받는다.
        const val UNKNOWN_CODE: String = "unknown"

        // rows 는 한 지역의 줄들이다. 시설 종류마다 한 줄씩 온다.
        fun from(code: String, region: RegionName, rows: List<RegionSpotTally>): RegionSafetySpotCount {
            val countsByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
            var fileCount: Int = 0
            var apiCount: Int = 0
            var latSum: Double = 0.0
            var lngSum: Double = 0.0
            var locatedCount: Int = 0
            for (row in rows) {
                val countSoFar: Int = countsByType[row.type] ?: 0
                countsByType[row.type] = countSoFar + row.total
                apiCount += row.apiCount
                fileCount += row.total - row.apiCount
                latSum += row.latSum
                lngSum += row.lngSum
                locatedCount += row.locatedCount
            }

            var center: GeoPoint? = null
            if (code != UNKNOWN_CODE && locatedCount > 0) {
                center = GeoPoint(lat = latSum / locatedCount, lng = lngSum / locatedCount)
            }

            return RegionSafetySpotCount(
                code = code,
                sido = region.sido,
                name = region.name,
                countsByType = countsByType,
                fileCount = fileCount,
                apiCount = apiCount,
                center = center,
            )
        }
    }
}
