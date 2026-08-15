package io.soo.bueongi.storage.db.core.facility.store

import io.soo.bueongi.storage.db.core.facility.model.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.model.SafePath
import io.soo.bueongi.core.enums.FacilityType

interface FacilitySpatialSource {
    fun index(type: FacilityType): GridSpatialIndex

    val safePaths: List<SafePath>

    val freshness: FacilityFreshness?
        get() = null
}
