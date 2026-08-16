package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference

/**
 * PII 최소화 — 좌표·토큰·검색어는 담지 않고 목적지 표시명(공개 지명)만 담는다.
 */
data class RouteRecommendationLog(
    val atEpochMs: Long,
    /** 추천 엔드포인트 종류: compare | safe-compare. */
    val endpoint: String,
    // 좌표만 오면 null 이 가능함
    val destinationName: String?,
    /** 저장 JSON 에는 requestValue(balanced|safe|safest)로 직렬화된다. */
    val preference: SafetyPreference,
)
