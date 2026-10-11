package io.soo.bueongi.storage.db.safetyspot.streetlamp

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class StreetLampSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    val standardSigunguCode: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.STREET_LAMP

    override fun regionCodeOrNull(): String? {
        return standardSigunguCode
    }

    override fun phoneOrNull(): String? {
        return null
    }
}
