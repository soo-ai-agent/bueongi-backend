package io.soo.bueongi.core.enums

/**
 * 안심 강도 선호 — 우회 허용 예산(기본 최단거리 대비 추가 허용 비율)을 정한다.
 * 문자열 요청값("balanced"|"safe"|"safest")의 타입 어휘 — 미상/누락은 [SAFE](안전 우선 기본).
 */
enum class SafetyPreference(val requestValue: String, val detourBudget: Double) {
    BALANCED("balanced", 0.2),
    SAFE("safe", 0.35),
    SAFEST("safest", 0.6),
    ;

    companion object {
        /** 요청 문자열 → 선호. 미상/누락이면 [SAFE]. */
        fun from(value: String?): SafetyPreference {
            val normalized = value?.trim()?.lowercase() ?: return SAFE
            return entries.firstOrNull { it.requestValue == normalized } ?: SAFE
        }
    }
}
