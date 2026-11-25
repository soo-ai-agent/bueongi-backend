package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotResponse
import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotsResponse
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidSpotLimitException
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionName
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionSafetySpotCount
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.RegionSafetySpots
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

@Service
class RegionSafetySpotStatusService(
    private val safetySpots: SafetySpotSpatialSource,
    private val regionNames: RegionNameCatalog,
) {
    fun summarize(): List<RegionSafetySpotResponse> {
        val talliesByRegion: LinkedHashMap<String, RegionTally> = LinkedHashMap()
        for (row in safetySpots.tallyByRegion()) {
            val tally: RegionTally = talliesByRegion.getOrPut(regionCodeOf(row.regionCode)) { RegionTally() }
            tally.add(row)
        }
        val rows: ArrayList<RegionSafetySpotCount> = ArrayList(talliesByRegion.size)
        for ((code, tally) in talliesByRegion) {
            rows.add(rowOf(code, tally))
        }
        rows.sortByDescending { it.total }
        return RegionSafetySpotResponse.from(rows)
    }

    fun findByCode(code: String, limit: Int): RegionSafetySpotsResponse {
        if (limit <= 0) {
            throw InvalidSpotLimitException(limit)
        }
        val budget: Int = limit.coerceAtMost(MAX_SPOTS)
        val pointsByType: Map<SafetySpotType, List<SafetySpotPoint>> = pointsByTypeOf(code)
        val region: RegionName = regionNames.findByCode(code)
        val found: RegionSafetySpots = RegionSafetySpots.from(code, region, pointsByType, budget)
        return RegionSafetySpotsResponse.from(found)
    }

    private fun pointsByTypeOf(code: String): Map<SafetySpotType, List<SafetySpotPoint>> {
        // 지역 코드가 없는 시설은 저장소에 빈 코드로 있다.
        var storedCode: String = code
        if (code == UNKNOWN_REGION) {
            storedCode = ""
        }
        val pointsByType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val inRegion: List<SafetySpotPoint> = safetySpots.findByRegion(type, storedCode)
            if (inRegion.isNotEmpty()) {
                pointsByType[type] = inRegion
            }
        }
        return pointsByType
    }

    private fun regionCodeOf(storedCode: String): String {
        val code: String = storedCode.trim()
        return code.ifEmpty { UNKNOWN_REGION }
    }

    private fun rowOf(code: String, tally: RegionTally): RegionSafetySpotCount {
        val region: RegionName = regionNames.findByCode(code)
        return RegionSafetySpotCount(
            code = code,
            sido = region.sido,
            name = region.name,
            countsByType = tally.countsByType,
            fileCount = tally.fileCount,
            apiCount = tally.apiCount,
            center = centerOf(code, tally),
        )
    }

    private fun centerOf(code: String, tally: RegionTally): GeoPoint? {
        if (code == UNKNOWN_REGION) {
            return null
        }
        return tally.center()
    }

    private companion object {
        const val UNKNOWN_REGION: String = "unknown"

        const val MAX_SPOTS: Int = 5_000
    }
}
