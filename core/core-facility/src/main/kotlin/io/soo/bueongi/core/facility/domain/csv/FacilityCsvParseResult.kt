package io.soo.bueongi.core.facility.domain.csv

import io.soo.bueongi.core.enums.FacilityCoordSystem

import io.soo.bueongi.storage.db.core.facility.FacilityPoint

data class FacilityCsvParseResult(
    val coordSystem: FacilityCoordSystem,
    val received: Int,
    val points: List<FacilityPoint>,
) {
    val skipped: Int get() = received - points.size
}
