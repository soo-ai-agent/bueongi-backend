package io.soo.bueongi.storage.db.safetyspot.pedestriansignal

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class PedestrianSignalSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    val openLocalGovCode: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.PEDESTRIAN_SIGNAL

    override fun regionCodeOrNull(): String? {
        return openLocalGovCode
    }

    override fun phoneOrNull(): String? {
        return null
    }
}
