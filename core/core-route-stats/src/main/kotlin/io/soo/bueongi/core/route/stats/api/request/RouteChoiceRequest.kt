package io.soo.bueongi.core.route.stats.api.request

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.stats.domain.model.RouteChoice
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 좌표·경로선·출발지는 받지 않는다 — 안전 점수 검증에 필요 없고, 받으면 이동 궤적이 된다.
 * 목적지는 추천 기록([io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog])과
 * 같은 수준으로 표시명만 받는다.
 *
 * 숫자 칸을 non-null 로 선언하면 Jackson 이 빠진 값에 0 을 채워 넣어 "0m 를 골랐다"가 기록된다.
 * nullable + @NotNull 조합이 그 자리를 막고, 어느 칸이 빠졌는지 응답에 담는다.
 */
data class RouteChoiceRequest(
    @field:NotNull
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val chosenType: RouteType?,
    @field:NotNull
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val safetyPreference: SafetyPreference?,
    @field:NotNull
    @field:Min(1)
    @field:Max(MAX_CANDIDATE_COUNT)
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val candidateCount: Int?,
    @field:NotNull
    @field:Min(0)
    @field:Max(MAX_DISTANCE_METERS)
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val distanceMeters: Int?,
    @field:NotNull
    @field:Min(0)
    @field:Max(MAX_DURATION_SECONDS)
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val durationSeconds: Int?,
    @field:Min(0)
    @field:Max(MAX_SCORE)
    // 점수 없는 폴백 경로(직선 추정)를 고르면 null 이 가능함
    val score: Int? = null,
    @field:Size(max = MAX_DESTINATION_NAME_LENGTH)
    // 지도를 눌러 찍은 목적지는 이름이 없어 null 이 가능함
    val destinationName: String? = null,
) {
    fun toChoice(): RouteChoice =
        RouteChoice(
            chosenType = requireNotNull(chosenType) { "chosenType 은 @NotNull 검증을 통과했어야 한다" },
            candidateCount = requireNotNull(candidateCount) { "candidateCount 도 동일" },
            score = score,
            distanceMeters = requireNotNull(distanceMeters) { "distanceMeters 도 동일" },
            durationSeconds = requireNotNull(durationSeconds) { "durationSeconds 도 동일" },
            preference = requireNotNull(safetyPreference) { "safetyPreference 도 동일" },
            destinationName = destinationName,
        )
}

/** 비교 화면이 내는 후보는 경로 종류 수만큼이다. 넉넉히 잡되 무한정 받지는 않는다. */
const val MAX_CANDIDATE_COUNT: Long = 10L

/** 도보 경로라 이보다 멀면 잘못 계산된 값이다. */
const val MAX_DISTANCE_METERS: Long = 200_000L

const val MAX_DURATION_SECONDS: Long = 86_400L

/** 안심 점수는 0~100 (프론트 계약). */
const val MAX_SCORE: Long = 100L

const val MAX_DESTINATION_NAME_LENGTH: Int = 100
