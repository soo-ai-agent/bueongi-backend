package io.soo.bueongi.storage.db.core.safetyspot.store

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.core.enums.SafetySpotType

interface SafetySpotSpatialSource {
    fun index(type: SafetySpotType): GridSpatialIndex

    val safePaths: List<SafePath>

    // manifest 가 없는 원천은 기준일을 모른다.
    val freshness: SafetySpotFreshness?
        get() = null
}
