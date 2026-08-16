package io.soo.bueongi.core.ops.api.request

import io.soo.bueongi.core.ops.domain.slack.model.SlackConfigUpdate
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class SlackConfigUpdateRequest(
    @field:NotNull
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val enabled: Boolean?,
    @field:NotNull
    @field:Min(1) @field:Max(1440)
    // 안 보내면 null 이 가능함 — 그래야 어느 필드가 빠졌는지 알려준다
    val throttleMinutes: Int?,
    // 끌 항목이 없으면 null 이 가능함
    val mutedCategories: List<String>? = null,
) {
    fun toUpdate(): SlackConfigUpdate =
        SlackConfigUpdate(
            enabled = requireNotNull(enabled) { "enabled 는 @NotNull 검증을 통과했어야 한다" },
            throttleMinutes = requireNotNull(throttleMinutes) { "throttleMinutes 는 @NotNull 검증을 통과했어야 한다" },
            mutedCategories = mutedCategories.orEmpty().toSet(),
        )
}
