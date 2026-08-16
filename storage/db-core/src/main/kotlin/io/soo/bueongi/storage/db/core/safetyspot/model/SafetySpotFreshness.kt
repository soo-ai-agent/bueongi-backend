package io.soo.bueongi.storage.db.core.safetyspot.model

data class SafetySpotFreshness(
    // manifest 에 없는 원천은 null 이 가능함
    val generatedAt: String? = null,
    // manifest 에 없는 원천은 null 이 가능함
    val version: String? = null,
)
