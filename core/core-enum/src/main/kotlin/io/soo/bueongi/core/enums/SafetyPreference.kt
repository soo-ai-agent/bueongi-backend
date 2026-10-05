package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// requestValue 가 와이어·저장 계약(JsonValue) — 정확히 balanced|safe|safest 를 내야 한다.
enum class SafetyPreference(
    @get:JsonValue val requestValue: String,
) {
    BALANCED("balanced"),
    SAFE("safe"),
    SAFEST("safest"),
    ;

    companion object {
        fun of(value: String?): SafetyPreference {
            if (value == null) {
                return SAFE
            }
            val normalized: String = value.trim().lowercase()
            for (preference in entries) {
                if (preference.requestValue == normalized) {
                    return preference
                }
            }
            return SAFE
        }
    }
}
