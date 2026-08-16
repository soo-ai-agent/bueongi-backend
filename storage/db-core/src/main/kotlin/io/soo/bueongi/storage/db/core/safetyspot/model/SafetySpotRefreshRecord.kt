package io.soo.bueongi.storage.db.core.safetyspot.model

/**
 * [replacedCount] 비null=교체 성공, null=실패·0건으로 기존 유지, 기록 부재=갱신 시도 없음(never).
 * 한 번도 갱신하지 않았으면 기록이 비어 있다.
 */
data class SafetySpotRefreshRecord(
    val atEpochMs: Long,
    val replacedCount: Int?,
)
