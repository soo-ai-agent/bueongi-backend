package io.soo.bueongi.storage.db.core.safetyspot.model

import io.soo.bueongi.core.enums.SafetySpotRefreshStatus

/**
 * [replacedCount] 비null=교체 성공, null=실패·0건으로 기존 유지, 기록 부재=갱신 시도 없음(never).
 */
data class SafetySpotRefreshRecord(
    val atEpochMs: Long,
    // 갱신이 실패하거나 0건이면 null 이 가능함
    val replacedCount: Int?,
) {
    fun status(): SafetySpotRefreshStatus {
        if (replacedCount != null) {
            return SafetySpotRefreshStatus.REPLACED
        }
        return SafetySpotRefreshStatus.KEPT
    }
}
