package io.soo.bueongi.core.route.api.request

import io.soo.bueongi.core.enums.SafetyPreference

/** 안심 강도. 모르는 값·빈 값은 가장 안전한 SAFE 로 읽는다(길 안내를 막지 않는다). */
fun toSafetyPreference(value: String?): SafetyPreference {
    val normalized: String = value?.trim()?.lowercase().orEmpty()
    for (preference in SafetyPreference.entries) {
        if (preference.requestValue == normalized) {
            return preference
        }
    }
    return SafetyPreference.SAFE
}
