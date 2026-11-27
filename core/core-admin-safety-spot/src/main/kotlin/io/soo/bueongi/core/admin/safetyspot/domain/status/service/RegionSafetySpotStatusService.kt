package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotsResponse
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidSpotLimitException
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionName
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionSafetySpots
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.RegionSpotTally
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

@Service
class RegionSafetySpotStatusService(
    private val safetySpotSource: SafetySpotSpatialSource,
    private val regionNames: RegionNameCatalog,
) {
    fun list(): List<RegionSafetySpotResponse> {
        val rowsByCode: LinkedHashMap<String, ArrayList<RegionSpotTally>> = LinkedHashMap()
        for (row in safetySpotSource.tallyByRegion()) {
            val code: String = shownCodeOf(row.regionCode)
            rowsByCode.getOrPut(code) { ArrayList() }.add(row)
        }

        val counts: ArrayList<RegionSafetySpotCount> = ArrayList(rowsByCode.size)
        for ((code, rows) in rowsByCode) {
            val region: RegionName = regionNames.findByCode(code)
            counts.add(RegionSafetySpotCount.from(code, region, rows))
        }
        counts.sortByDescending { it.total }
        return RegionSafetySpotResponse.from(counts)
    }

    fun findByCode(code: String, limit: Int): RegionSafetySpotsResponse {
        if (limit <= 0) {
            throw InvalidSpotLimitException(limit)
        }
        val maxCount: Int = minOf(limit, MAX_SPOTS)
        val pointsByType: Map<SafetySpotType, List<SafetySpotPoint>> = findPointsByType(code)
        val region: RegionName = regionNames.findByCode(code)
        val found: RegionSafetySpots = RegionSafetySpots.from(code, region, pointsByType, maxCount)
        return RegionSafetySpotsResponse.from(found)
    }

    private fun findPointsByType(code: String): Map<SafetySpotType, List<SafetySpotPoint>> {
        val storedCode: String = storedCodeOf(code)
        val pointsByType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val points: List<SafetySpotPoint> = safetySpotSource.findByRegion(type, storedCode)
            if (points.isNotEmpty()) {
                pointsByType[type] = points
            }
        }
        return pointsByType
    }

    // 지역 코드가 없는 시설은 저장소에 빈 코드로 있다. 화면에는 "unknown" 으로 보여 준다.
    private fun shownCodeOf(storedCode: String): String {
        val code: String = storedCode.trim()
        if (code.isEmpty()) {
            return RegionSafetySpotCount.UNKNOWN_CODE
        }
        return code
    }

    private fun storedCodeOf(shownCode: String): String {
        if (shownCode == RegionSafetySpotCount.UNKNOWN_CODE) {
            return ""
        }
        return shownCode
    }

    private companion object {
        const val MAX_SPOTS: Int = 5_000
    }
}
