package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

@Service
class FacilityStatusService(
    private val facilitySource: FacilitySpatialSource,
    private val refreshStatusStore: FacilityRefreshStatusStore,
) {

    fun summarize(): FacilityStatusSummary {
        val freshness: FacilityFreshness? = facilitySource.freshness
        val facilities: List<FacilityStatus> = FacilityType.entries.map { type ->
            val record: FacilityRefreshRecord? = refreshStatusStore.findByType(type)
            FacilityStatus(
                type = type,
                loadedCount = facilitySource.index(type).size,
                lastRefreshAtMs = record?.atEpochMs,
                lastRefreshCount = record?.replacedCount,
                lastRefreshStatus = statusOf(record?.replacedCount, attempted = record != null),
                cdnGeneratedAt = freshness?.generatedAt,
                cdnVersion = freshness?.version,
            )
        }
        return FacilityStatusSummary(facilities = facilities)
    }

    private fun statusOf(replacedCount: Int?, attempted: Boolean): FacilityRefreshStatus {
        if (!attempted) {
            return FacilityRefreshStatus.NEVER
        }
        if (replacedCount != null) {
            return FacilityRefreshStatus.REPLACED
        }
        return FacilityRefreshStatus.KEPT
    }
}
