package io.soo.bueongi.core.admin.safetyspot.domain.csv.model

import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class SafetySpotCsvReadResult(
    val coordSystem: SafetySpotCoordSystem,
    val received: Int,
    val points: List<SafetySpotPoint>,
) {
    val skipped: Int get() = received - points.size
}
