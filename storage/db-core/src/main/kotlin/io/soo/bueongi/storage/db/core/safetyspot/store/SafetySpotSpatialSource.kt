package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.core.enums.SafetySpotType

interface SafetySpotSpatialSource {
    fun index(type: SafetySpotType): GridSpatialIndex

    val safePaths: List<SafePath>

    val freshness: SafetySpotFreshness?
        get() = null
}
