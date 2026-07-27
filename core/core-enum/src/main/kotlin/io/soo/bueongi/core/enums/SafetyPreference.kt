package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 안심 강도 선호 — 우회 허용 예산(기본 최단거리 대비 추가 허용 비율)을 정한다.
 * 문자열 요청값("balanced"|"safe"|"safest")의 타입 어휘 — 미상/누락은 [SAFE](안전 우선 기본).
 *
 * [requestValue] 가 와이어 문자열이다([JsonValue]) — 이 값이 저장(추천 통계 로그)·응답(관리자 선호 분포) 계약이라
 * 직렬화가 정확히 balanced|safe|safest 를 내야 한다. 역직렬화도 [from] 으로 고정한다([JsonCreator]).
 */
enum class SafetyPreference(@get:JsonValue val requestValue: String, val detourBudget: Double) {
    BALANCED("balanced", 0.2),
    SAFE("safe", 0.35),
    SAFEST("safest", 0.6),
    ;

    companion object {
        /** 요청/저장 문자열 → 선호. 미상/누락이면 [SAFE](요청 경계의 관용 기본과 동일). */
        @JsonCreator
        @JvmStatic
        fun from(value: String?): SafetyPreference {
            val normalized = value?.trim()?.lowercase() ?: return SAFE
            return entries.firstOrNull { it.requestValue == normalized } ?: SAFE
        }
    }
}
