package io.soo.bueongi.core.route.stats.api.request

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.stats.domain.model.RouteChoice
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// 좌표·경로선·출발지는 받지 않는다 — 받으면 이동 궤적이 된다.
data class RouteChoiceRequest(
    @field:NotNull
    val chosenType: RouteType?,
    @field:NotNull
    val safetyPreference: SafetyPreference?,
    @field:NotNull
    @field:Min(1)
    @field:Max(MAX_CANDIDATE_COUNT)
    val candidateCount: Int?,
    @field:NotNull
    @field:Min(0)
    @field:Max(MAX_DISTANCE_METERS)
    val distanceMeters: Int?,
    @field:NotNull
    @field:Min(0)
    @field:Max(MAX_DURATION_SECONDS)
    val durationSeconds: Int?,
    @field:Min(0)
    @field:Max(MAX_SCORE)
    val score: Int? = null,
    @field:Size(max = MAX_DESTINATION_NAME_LENGTH)
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

const val MAX_CANDIDATE_COUNT: Long = 10L

const val MAX_DISTANCE_METERS: Long = 200_000L

const val MAX_DURATION_SECONDS: Long = 86_400L

// 안심 점수는 0~100 (프론트 계약).
const val MAX_SCORE: Long = 100L

const val MAX_DESTINATION_NAME_LENGTH: Int = 100
