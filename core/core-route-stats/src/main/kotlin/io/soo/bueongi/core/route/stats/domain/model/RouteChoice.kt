package io.soo.bueongi.core.route.stats.domain.model

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference

/** 기록할 선택 하나. 언제 골랐는지는 서비스가 시계에서 붙인다(클라이언트 시각을 믿지 않는다). */
data class RouteChoice(
    val chosenType: RouteType,
    val candidateCount: Int,
    // 점수 없는 폴백 경로를 고르면 null 이 가능함
    val score: Int?,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val preference: SafetyPreference,
    // 지도를 눌러 찍은 목적지는 이름이 없어 null 이 가능함
    val destinationName: String?,
)
