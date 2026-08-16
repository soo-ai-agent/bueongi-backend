package io.soo.bueongi.storage.db.core.safetyspot.model

/** manifest 가 없는 원천은 기준일을 모른다. */
data class SafetySpotFreshness(
    val generatedAt: String? = null,
    val version: String? = null,
)
