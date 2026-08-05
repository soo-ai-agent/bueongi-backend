package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

/**
 * 관리자 페이지용 "시설 데이터 현황" 집계 — 종류(5종) 각각의 현재 적재 건수 + 마지막 라이브 갱신 시각·결과 + CDN 신선도를 모은다.
 * 적재 건수는 저장소 공간 인덱스([FacilitySpatialSource.index])에서, 갱신 결과는 인메모리 홀더([FacilityRefreshStatusStore])에서,
 * CDN 신선도는 manifest 기반 [FacilitySpatialSource.freshness]에서 읽는다(집계만, 새 저장소·부수효과 없음).
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
                lastRefreshStatus = statusOf(record?.replacedCount, attempted = record != null),
                cdnGeneratedAt = freshness?.generatedAt,
                cdnVersion = freshness?.version,
            )
        }
        return FacilityStatusSummary(facilities = facilities)
    }

    /** 갱신 기록 없음=NEVER, 있으면 교체 건수 유무로 REPLACED(성공)/KEPT(수집 실패·0건 유지) 판정. */
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
