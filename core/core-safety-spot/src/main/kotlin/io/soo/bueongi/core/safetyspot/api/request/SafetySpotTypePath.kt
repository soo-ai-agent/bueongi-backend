package io.soo.bueongi.core.safetyspot.api.request

import io.soo.bueongi.core.safetyspot.domain.error.UnknownSafetySpotTypeException
import io.soo.bueongi.core.enums.SafetySpotType

/** 경로 변수의 "cctv" 같은 문자열을 시설 종류로 바꾼다. 모르는 값이면 400 으로 돌린다. */
fun toValidatedSafetySpotType(cdnType: String): SafetySpotType {
    for (type in SafetySpotType.entries) {
        if (type.cdnType == cdnType) {
            return type
        }
    }
    throw UnknownSafetySpotTypeException(cdnType, allowedCdnTypes())
}

private fun allowedCdnTypes(): List<String> {
    val allowed: ArrayList<String> = ArrayList<String>()
    for (type in SafetySpotType.entries) {
        allowed.add(type.cdnType)
    }
    return allowed
}
