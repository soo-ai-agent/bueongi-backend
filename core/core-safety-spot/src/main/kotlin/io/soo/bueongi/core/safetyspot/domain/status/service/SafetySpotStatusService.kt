package io.soo.bueongi.core.safetyspot.domain.status.service

import io.soo.bueongi.core.safetyspot.domain.status.model.SafetySpotStatus
import io.soo.bueongi.core.safetyspot.domain.status.model.SafetySpotStatusSummary
import io.soo.bueongi.core.enums.SafetySpotRefreshStatus

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import org.springframework.stereotype.Service

/**
 * 안심시설 데이터 현황. 시설 데이터는 파이프라인이 만든 CDN 파일이 원천이고,
 * 백엔드는 그 파일과 관리자 CSV 업로드분만 적재한다(외부 API 를 직접 부르지 않는다).
 * 그래서 갱신 기록이 있는 종류는 업로드분이고, 없으면 CDN 파일 그대로다.
 */
@Service
class SafetySpotStatusService(
    private val safetySpotSource: SafetySpotSpatialSource,
    private val refreshStatusStore: SafetySpotRefreshStatusStore,
) {

    fun summarize(): SafetySpotStatusSummary {
        val freshness: SafetySpotFreshness? = safetySpotSource.freshness
        val safetySpots: ArrayList<SafetySpotStatus> = ArrayList<SafetySpotStatus>()
        for (type in SafetySpotType.entries) {
            val record: SafetySpotRefreshRecord? = refreshStatusStore.findByType(type)
            safetySpots.add(
                SafetySpotStatus(
                    type = type,
                    loadedCount = safetySpotSource.index(type).size,
                    lastRefreshAtMs = record?.atEpochMs,
                    lastRefreshCount = record?.replacedCount,
                    lastRefreshStatus = statusOf(record),
                    cdnGeneratedAt = freshness?.generatedAt,
                    cdnVersion = freshness?.version,
                ),
            )
        }
        return SafetySpotStatusSummary(safetySpots = safetySpots)
    }

    private fun statusOf(record: SafetySpotRefreshRecord?): SafetySpotRefreshStatus {
        if (record == null) {
            return SafetySpotRefreshStatus.SKIPPED
        }
        if (record.replacedCount != null) {
            return SafetySpotRefreshStatus.REPLACED
        }
        return SafetySpotRefreshStatus.KEPT
    }
}
