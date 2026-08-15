package io.soo.bueongi.core.safetyspot.domain.model

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint

internal data class SafetySpotWithDistance(val point: SafetySpotPoint, val distanceMeters: Double)
