package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.LiveFacilitySources
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

@Service
class FacilityStatusService(
    private val facilitySource: FacilitySpatialSource,
    private val refreshStatusStore: FacilityRefreshStatusStore,
    private val liveSources: LiveFacilitySources,
) {

    fun summarize(): FacilityStatusSummary {
        val freshness: FacilityFreshness? = facilitySource.freshness
        val liveTypes: Set<FacilityType> = liveSources.liveRefreshableTypes()
        val facilities: List<FacilityStatus> = FacilityType.entries.map { type ->
            val record: FacilityRefreshRecord? = refreshStatusStore.findByType(type)
            FacilityStatus(
                type = type,
                loadedCount = facilitySource.index(type).size,
                lastRefreshAtMs = record?.atEpochMs,
                lastRefreshCount = record?.replacedCount,
                lastRefreshStatus = statusOf(record, liveCapable = type in liveTypes),
                cdnGeneratedAt = freshness?.generatedAt,
                cdnVersion = freshness?.version,
            )
        }
        return FacilityStatusSummary(facilities = facilities)
    }

    private fun statusOf(record: FacilityRefreshRecord?, liveCapable: Boolean): FacilityRefreshStatus {
        if (record == null) {
            if (liveCapable) {
                return FacilityRefreshStatus.NEVER
            }
            return FacilityRefreshStatus.SKIPPED
        }
        if (record.replacedCount != null) {
            return FacilityRefreshStatus.REPLACED
        }
        return FacilityRefreshStatus.KEPT
    }
}
