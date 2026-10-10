package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotStatusResponse
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.SafetySpotStatus
import io.soo.bueongi.core.enums.SafetySpotRefreshStatus
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

@Service
class SafetySpotStatusService(
    private val safetySpotSource: SafetySpotSpatialSource,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
) {
    fun summarize(): SafetySpotStatusResponse {
        val freshness: SafetySpotFreshness? = safetySpotSource.freshness
        val safetySpots: ArrayList<SafetySpotStatus> = ArrayList()
        for (type in SafetySpotType.entries) {
            val record: SafetySpotRefreshRecord? = refreshStatusStore.findByType(type)
            safetySpots.add(
                SafetySpotStatus(
                    type = type,
                    loadedCount = safetySpotSource.index(type).size,
                    lastRefreshAtMs = record?.atEpochMs,
                    lastRefreshCount = record?.replacedCount,
                    lastRefreshStatus = record?.status() ?: SafetySpotRefreshStatus.SKIPPED,
                    cdnGeneratedAt = freshness?.generatedAt,
                    cdnVersion = freshness?.version,
                ),
            )
        }
        return SafetySpotStatusResponse.from(safetySpots)
    }
}
