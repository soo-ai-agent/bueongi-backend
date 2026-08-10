package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType
import org.springframework.stereotype.Component

/** [replacedCount] 비null=교체 성공, null=실패·0건으로 기존 유지, 기록 부재=갱신 시도 없음(never). */
data class FacilityRefreshRecord(
    val atEpochMs: Long,
    val replacedCount: Int?,
)
