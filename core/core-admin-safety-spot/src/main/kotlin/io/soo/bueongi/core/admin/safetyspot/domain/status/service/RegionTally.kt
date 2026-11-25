package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.RegionSpotTally

internal class RegionTally {
    val countsByType: LinkedHashMap<SafetySpotType, Int> = LinkedHashMap()
    var fileCount: Int = 0
    var apiCount: Int = 0
    private var latSum: Double = 0.0
    private var lngSum: Double = 0.0
    private var locatedCount: Int = 0

    fun add(row: RegionSpotTally) {
        countsByType[row.type] = (countsByType[row.type] ?: 0) + row.total
        apiCount += row.apiCount
        fileCount += row.total - row.apiCount
        latSum += row.latSum
        lngSum += row.lngSum
        locatedCount += row.locatedCount
    }

    fun center(): GeoPoint? {
        if (locatedCount == 0) {
            return null
        }
        return GeoPoint(lat = latSum / locatedCount, lng = lngSum / locatedCount)
    }
}
