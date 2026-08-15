package io.soo.bueongi.core.facility.api.request

import io.soo.bueongi.core.facility.domain.error.UnknownFacilityTypeException
import io.soo.bueongi.core.enums.FacilityType

/** 경로 변수의 "cctv" 같은 문자열을 시설 종류로 바꾼다. 모르는 값이면 400 으로 돌린다. */
fun toValidatedFacilityType(cdnType: String): FacilityType {
    for (type in FacilityType.entries) {
        if (type.cdnType == cdnType) {
            return type
        }
    }
    throw UnknownFacilityTypeException(cdnType, allowedCdnTypes())
}

private fun allowedCdnTypes(): List<String> {
    val allowed: ArrayList<String> = ArrayList<String>()
    for (type in FacilityType.entries) {
        allowed.add(type.cdnType)
    }
    return allowed
}
