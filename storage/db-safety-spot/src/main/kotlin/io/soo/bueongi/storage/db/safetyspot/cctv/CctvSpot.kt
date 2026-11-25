package io.soo.bueongi.storage.db.safetyspot.cctv

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint

data class CctvSpot(
    override val lat: Double,
    override val lng: Double,
    override val name: String? = null,
    override val address: String? = null,
    val openLocalGovCode: String? = null,
    val purpose: String? = null,
    val cameraCount: Int? = null,
) : SafetySpotPoint() {
    override val type: SafetySpotType = SafetySpotType.CCTV

    override fun regionCodeOrNull(): String? {
        return openLocalGovCode
    }

    override fun phoneOrNull(): String? {
        return null
    }

    override fun purposeOrNull(): String? {
        return purpose
    }

    override fun cameraCountOrNull(): Int? {
        return cameraCount
    }
}
