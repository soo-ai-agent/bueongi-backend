package io.soo.bueongi.core.route.domain.safety
import io.soo.bueongi.storage.db.core.facility.GeoPoint

data class SafeAnchor(
    val center: GeoPoint,
    val weight: Int,
)
