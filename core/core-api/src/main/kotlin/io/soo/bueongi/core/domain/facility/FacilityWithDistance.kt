package io.soo.bueongi.core.domain.facility

import io.soo.bueongi.storage.db.core.facility.FacilityPoint

internal data class FacilityWithDistance(val point: FacilityPoint, val distanceMeters: Double)
