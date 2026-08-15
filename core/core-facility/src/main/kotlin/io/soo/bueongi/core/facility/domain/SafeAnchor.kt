package io.soo.bueongi.core.facility.domain
import io.soo.bueongi.storage.db.core.facility.GeoPoint

data class SafeAnchor(
    val center: GeoPoint,
    val weight: Int,
)
