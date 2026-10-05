package io.soo.bueongi.core.route.domain.safety.model
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint

data class SafetySpotCluster(
    val center: GeoPoint,
    val weight: Int,
)
