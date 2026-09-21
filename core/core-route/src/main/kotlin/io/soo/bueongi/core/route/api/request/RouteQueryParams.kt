package io.soo.bueongi.core.route.api.request

import io.soo.bueongi.core.enums.SafetyPreference

fun toSafetyPreference(value: String?): SafetyPreference {
    val normalized: String = value?.trim()?.lowercase().orEmpty()
    for (preference in SafetyPreference.entries) {
        if (preference.requestValue == normalized) {
            return preference
        }
    }
    return SafetyPreference.SAFE
}
