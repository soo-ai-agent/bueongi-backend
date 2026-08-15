package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

/** [requestValue] 가 와이어·저장 계약([JsonValue]) — 정확히 balanced|safe|safest 를 내야 한다. */
enum class SafetyPreference(@get:JsonValue val requestValue: String, val detourBudget: Double) {
    BALANCED("balanced", 0.2),
    SAFE("safe", 0.35),
    SAFEST("safest", 0.6),
}
