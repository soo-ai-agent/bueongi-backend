package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.support.util.isInKorea

internal class RegionTally {
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
