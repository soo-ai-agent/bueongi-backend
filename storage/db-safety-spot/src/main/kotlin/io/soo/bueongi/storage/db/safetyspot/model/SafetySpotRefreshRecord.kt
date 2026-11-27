package io.soo.bueongi.storage.db.safetyspot.model

data class SafetySpotRefreshRecord(
    val savedAtMs: Long,
    val replacedCount: Int,
)
