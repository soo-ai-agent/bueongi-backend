package io.soo.bueongi.core.admin.safetyspot.domain.status.model

import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class RegionSafetySpots(
    val code: String,
    val sido: String,
    val name: String,
    val total: Int,
    val spots: List<SafetySpotPoint>,
)
