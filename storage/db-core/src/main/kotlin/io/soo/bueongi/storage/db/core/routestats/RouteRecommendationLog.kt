package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.SafetyPreference

// PII 최소화 — 좌표·토큰·검색어는 담지 않고 목적지 표시명(공개 지명)만 담는다.
data class RouteRecommendationLog(
    val atEpochMs: Long,
    // 허용값 compare | safe-compare.
    val endpoint: String,
    val destinationName: String?,
    // 저장 JSON 에는 requestValue(balanced|safe|safest)로 직렬화된다.
    val preference: SafetyPreference,
)
