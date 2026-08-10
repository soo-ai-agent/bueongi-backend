package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType
import org.springframework.stereotype.Component

@Component
class FacilityRefreshStatusStore {

    @Volatile
    private var records: Map<FacilityType, FacilityRefreshRecord> = emptyMap()

    fun record(results: Map<FacilityType, Int?>, atEpochMs: Long) {
        records = results.mapValues { (_, count) -> FacilityRefreshRecord(atEpochMs = atEpochMs, replacedCount = count) }
    }

    fun recordUpload(type: FacilityType, replacedCount: Int, atEpochMs: Long) {
        records = records + (type to FacilityRefreshRecord(atEpochMs = atEpochMs, replacedCount = replacedCount))
    }

    fun findByType(type: FacilityType): FacilityRefreshRecord? = records[type]
}
