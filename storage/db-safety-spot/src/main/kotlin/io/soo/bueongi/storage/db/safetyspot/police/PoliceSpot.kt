package io.soo.bueongi.storage.db.safetyspot.police

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class PoliceSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    val standardSigunguCode: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.POLICE

    override fun regionCodeOrNull(): String? {
        return standardSigunguCode
    }

    override fun phoneOrNull(): String? {
        return phone
    }
}
