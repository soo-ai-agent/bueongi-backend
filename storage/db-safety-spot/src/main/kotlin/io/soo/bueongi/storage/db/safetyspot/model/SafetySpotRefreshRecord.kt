package io.soo.bueongi.storage.db.safetyspot.model

data class SafetySpotRefreshRecord(
    val atEpochMs: Long,
    val replacedCount: Int,
)
