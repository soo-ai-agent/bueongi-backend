package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType

data class SafePath(
    val coords: List<GeoPoint>,
    val name: String? = null,
)
