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

    fun findSpotsByCode(code: String, limit: Int): RegionSafetySpotsResponse {
        if (limit <= 0) {
            throw InvalidSpotLimitException(limit)
        }
        val budget: Int = limit.coerceAtMost(MAX_SPOTS)
        val pointsByType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val inRegion: List<SafetySpotPoint> = safetySpots.index(type).all.filter { regionCodeOf(it) == code }
            if (inRegion.isNotEmpty()) {
                pointsByType[type] = inRegion
            }
        }
        val quotas: Map<SafetySpotType, Int> = roundRobinQuotasOf(pointsByType, budget)
        val shown: ArrayList<SafetySpotPoint> = ArrayList(budget)
        for ((type, points) in pointsByType) {
            shown.addAll(spreadEvenly(points, quotas.getValue(type)))
        }
        val region: RegionName = regionNameOf(code)
        val found: RegionSafetySpots =
            RegionSafetySpots(
                code = code,
                sido = region.sido,
                name = region.name,
                total = pointsByType.values.sumOf { it.size },
                spots = shown,
            )
        return RegionSafetySpotsResponse.from(found)
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

    private fun spreadEvenly(points: List<SafetySpotPoint>, quota: Int): List<SafetySpotPoint> {
        if (quota >= points.size) {
            return points
        }
        val picked: ArrayList<SafetySpotPoint> = ArrayList(quota)
        for (slot in 0 until quota) {
            picked.add(points[slot * points.size / quota])
        }
        return picked
    }

    private fun regionCodeOf(point: SafetySpotPoint): String {
        val code: String? = point.regionCodeOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return code ?: UNKNOWN_REGION
    }

    private fun regionNameOf(code: String): RegionName {
        val known: RegionName? = regionNames.findByCode(code)
        if (known == null) {
            return RegionName(sido = "", name = code)
        }
        return known
    }

    private fun rowOf(code: String, tally: RegionTally): RegionSafetySpotCount {
        val region: RegionName = regionNameOf(code)
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
