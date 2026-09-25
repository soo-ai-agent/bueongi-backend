package io.soo.bueongi.core.admin.safetyspot.domain.csv.model

import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

data class SafetySpotCsvParseResult(val coordSystem: SafetySpotCoordSystem, val received: Int, val points: List<SafetySpotPoint>) {
    val skipped: Int get() = received - points.size
}
