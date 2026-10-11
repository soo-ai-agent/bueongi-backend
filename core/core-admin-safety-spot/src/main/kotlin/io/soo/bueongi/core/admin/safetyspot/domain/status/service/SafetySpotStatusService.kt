package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotStatusResponse
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.SafetySpotStatus
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSources
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

@Service
class SafetySpotStatusService(
    private val safetySpotSource: SafetySpotSpatialSource,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
) {
    fun summarize(): SafetySpotStatusResponse {
        val safetySpots: ArrayList<SafetySpotStatus> = ArrayList()
        for (type in SafetySpotType.entries) {
            val record: SafetySpotRefreshRecord? = refreshStatusStore.findByType(type)
            val sources: SafetySpotSources = safetySpotSource.sources(type)
            safetySpots.add(
                SafetySpotStatus(
                    type = type,
                    fileCount = sources.fileOriginCount,
                    apiCount = sources.apiOriginCount,
                    lastRefreshAtMs = record?.atEpochMs,
                    lastRefreshCount = record?.replacedCount,
                ),
            )
        }
        return SafetySpotStatusResponse.from(safetySpots)
    }
}
