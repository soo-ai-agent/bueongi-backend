package io.soo.bueongi.core.admin.safetyspot.api.request

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.admin.safetyspot.domain.error.UnknownSafetySpotTypeException

fun toValidatedSafetySpotType(cdnType: String): SafetySpotType {
    for (type in SafetySpotType.entries) {
        if (type.cdnType == cdnType) {
            return type
        }
    }
    throw UnknownSafetySpotTypeException(cdnType, allowedCdnTypes())
}

private fun allowedCdnTypes(): List<String> {
    val allowed: ArrayList<String> = ArrayList()
    for (type in SafetySpotType.entries) {
        allowed.add(type.cdnType)
    }
    return allowed
}
