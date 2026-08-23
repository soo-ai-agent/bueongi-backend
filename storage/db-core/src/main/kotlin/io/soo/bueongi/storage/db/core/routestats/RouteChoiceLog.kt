package io.soo.bueongi.storage.db.core.routestats

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference

/**
 * 사용자가 비교 화면에서 실제로 고른 경로. PII 수준은 추천 기록([RouteRecommendationLog])과 같다 —
 * 좌표·경로선·출발지는 담지 않고 목적지 표시명(공개 지명)만 담는다.
 */
data class RouteChoiceLog(
    val atEpochMs: Long,
    val chosenType: RouteType,
    /** 고를 때 화면에 보였던 후보 수. 2개 중 1개와 5개 중 1개는 같은 선택이 아니다. */
    val candidateCount: Int,
    // 점수 없는 폴백 경로(직선 추정)를 고르면 null 이 가능함
    val score: Int?,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val preference: SafetyPreference,
    // 지도를 눌러 찍은 목적지는 이름이 없어 null 이 가능함
    val destinationName: String?,
)
