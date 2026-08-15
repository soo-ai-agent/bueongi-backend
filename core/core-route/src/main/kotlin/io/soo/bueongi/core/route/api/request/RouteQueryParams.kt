package io.soo.bueongi.core.route.api.request

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference

/**
 * 요청 본문의 경로 종류 문자열을 종류로 바꾼다.
 * 모르는 값·빈 값은 거절하지 않고 **가장 안전한 기본값(SAFE)** 으로 읽는다 —
 * 안심 앱에서 종류를 못 알아들었다고 길 안내를 막는 것보다 안전한 길을 주는 편이 낫다.
 */
fun toRouteType(value: String?): RouteType {
    val normalized: String = normalize(value)
    for (type in RouteType.entries) {
        if (type.apiType == normalized || type.id == normalized) {
            return type
        }
    }
    return RouteType.SAFE
}

/** 안심 강도. 같은 이유로 모르는 값은 SAFE 로 읽는다. */
fun toSafetyPreference(value: String?): SafetyPreference {
    val normalized: String = normalize(value)
    for (preference in SafetyPreference.entries) {
        if (preference.requestValue == normalized) {
            return preference
        }
    }
    return SafetyPreference.SAFE
}

private fun normalize(value: String?): String {
    if (value == null) {
        return ""
    }
    return value.trim().lowercase()
}
