package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference

// PII 최소화 — 좌표·경로선·출발지는 담지 않고 목적지 표시명(공개 지명)만 담는다.
data class RouteChoiceLog(
    val atEpochMs: Long,
    val chosenType: RouteType,
    val candidateCount: Int,
    val score: Int?,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val preference: SafetyPreference,
    val destinationName: String?,
)
