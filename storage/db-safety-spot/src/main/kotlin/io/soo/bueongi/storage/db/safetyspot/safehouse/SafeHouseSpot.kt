package io.soo.bueongi.storage.db.safetyspot.safehouse

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class SafeHouseSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    val openLocalGovCode: String? = null,
    val phone: String? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.SAFEHOUSE

    override fun regionCodeOrNull(): String? {
        return openLocalGovCode
    }

    override fun phoneOrNull(): String? {
        return phone
    }
}
