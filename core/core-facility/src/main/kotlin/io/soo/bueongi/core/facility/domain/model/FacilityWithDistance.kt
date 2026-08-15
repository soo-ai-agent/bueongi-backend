package io.soo.bueongi.core.facility.domain.model

import io.soo.bueongi.storage.db.core.facility.FacilityPoint

internal data class FacilityWithDistance(val point: FacilityPoint, val distanceMeters: Double)
