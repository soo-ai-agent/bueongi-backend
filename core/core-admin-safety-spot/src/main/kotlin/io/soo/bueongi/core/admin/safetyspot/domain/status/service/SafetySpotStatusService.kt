package io.soo.bueongi.core.admin.safetyspot.domain.status.service

import io.soo.bueongi.core.admin.safetyspot.api.response.SafetySpotStatusResponse
import io.soo.bueongi.core.admin.safetyspot.domain.status.model.SafetySpotStatus
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotOriginCount
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSource
import org.springframework.stereotype.Service

@Service
class SafetySpotStatusService(
    private val safetySpotSource: SafetySpotSource,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
) {
    fun find(): SafetySpotStatusResponse {
        val statuses: ArrayList<SafetySpotStatus> = ArrayList()
        for (type in SafetySpotType.entries) {
            val record: SafetySpotRefreshRecord? = refreshStatusStore.findByType(type)
            val count: SafetySpotOriginCount = safetySpotSource.countByOrigin(type)
            val status =
                SafetySpotStatus(
                    type = type,
                    fileCount = count.fileCount,
                    apiCount = count.apiCount,
                    lastRefreshAtMs = record?.savedAtMs,
                    lastRefreshCount = record?.replacedCount,
                )
            statuses.add(status)
        }
        return SafetySpotStatusResponse.from(statuses)
    }
}
