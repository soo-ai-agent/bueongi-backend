package io.soo.bueongi.core.facility.domain.status.service

import io.soo.bueongi.core.facility.domain.status.model.FacilityStatus
import io.soo.bueongi.core.facility.domain.status.model.FacilityStatusSummary
import io.soo.bueongi.core.enums.FacilityRefreshStatus

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.model.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.model.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.store.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.store.FacilitySpatialSource
import org.springframework.stereotype.Service

/**
 * 안심시설 데이터 현황. 시설 데이터는 파이프라인이 만든 CDN 파일이 원천이고,
 * 백엔드는 그 파일과 관리자 CSV 업로드분만 적재한다(외부 API 를 직접 부르지 않는다).
 * 그래서 갱신 기록이 있는 종류는 업로드분이고, 없으면 CDN 파일 그대로다.
 */
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
                lastRefreshStatus = statusOf(record),
                cdnGeneratedAt = freshness?.generatedAt,
                cdnVersion = freshness?.version,
            )
        }
        return FacilityStatusSummary(facilities = facilities)
    }

    private fun statusOf(record: FacilityRefreshRecord?): FacilityRefreshStatus {
        if (record == null) {
            return FacilityRefreshStatus.SKIPPED
        }
        if (record.replacedCount != null) {
            return FacilityRefreshStatus.REPLACED
        }
        return FacilityRefreshStatus.KEPT
    }
}
