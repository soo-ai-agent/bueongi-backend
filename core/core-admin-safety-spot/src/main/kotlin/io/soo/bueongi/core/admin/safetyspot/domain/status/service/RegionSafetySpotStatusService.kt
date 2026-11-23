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
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSources
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.support.util.isInKorea
import org.springframework.stereotype.Service

@Service
class RegionSafetySpotStatusService(
    private val safetySpots: SafetySpotSpatialSource,
    private val regionNames: RegionNameCatalog,
) {
    fun summarize(): List<RegionSafetySpotResponse> {
        val talliesByRegion: LinkedHashMap<String, RegionTally> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val sources: SafetySpotSources = safetySpots.sources(type)
            for (point in sources.index.all) {
                val tally: RegionTally = talliesByRegion.getOrPut(regionCodeOf(point)) { RegionTally() }
                tally.add(type, point, fromApi = sources.isCollectedFromApi(point))
            }
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
        val pointsByType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val inRegion: List<SafetySpotPoint> = safetySpots.index(type).all.filter { regionCodeOf(it) == code }
            if (inRegion.isNotEmpty()) {
                pointsByType[type] = inRegion
            }
        }
        return pointsByType
    }

    private fun regionCodeOf(point: SafetySpotPoint): String {
        val code: String = point.regionCodeOrNull().orEmpty().trim()
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

    private class RegionTally {
        val countsByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
        var fileCount: Int = 0
        var apiCount: Int = 0
        private var latSum: Double = 0.0
        private var lngSum: Double = 0.0
        private var locatedCount: Int = 0

        fun add(type: SafetySpotType, point: SafetySpotPoint, fromApi: Boolean) {
            countsByType[type] = (countsByType[type] ?: 0) + 1
            if (fromApi) {
                apiCount++
            } else {
                fileCount++
            }
            if (!isInKorea(point.lat, point.lng)) {
                return
            }
            latSum += point.lat
            lngSum += point.lng
            locatedCount++
        }

        fun center(): GeoPoint? {
            if (locatedCount == 0) {
                return null
            }
            return GeoPoint(lat = latSum / locatedCount, lng = lngSum / locatedCount)
        }
    }

    private companion object {
        const val UNKNOWN_REGION: String = "unknown"

        const val MAX_SPOTS: Int = 5_000
    }
}
