package io.soo.bueongi.core.route.safety.model
import io.soo.bueongi.storage.db.core.facility.GeoPoint

data class SafeAnchor(
    val center: GeoPoint,
    val weight: Int,
)
