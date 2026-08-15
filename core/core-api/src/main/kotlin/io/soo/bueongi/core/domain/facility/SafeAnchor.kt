package io.soo.bueongi.core.domain.facility
import io.soo.bueongi.storage.db.core.facility.GeoPoint

data class SafeAnchor(
    val center: GeoPoint,
    val weight: Int,
)
