package io.soo.bueongi.admin.request

import io.soo.bueongi.admin.SlackConfigUpdate
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class SlackConfigUpdateRequest(
    @field:NotNull
    val enabled: Boolean?,
    @field:NotNull
    @field:Min(1) @field:Max(1440)
    val throttleMinutes: Int?,
    val mutedCategories: List<String>? = null,
) {
    fun toUpdate(): SlackConfigUpdate = SlackConfigUpdate(
        enabled = requireNotNull(enabled) { "enabled 는 @NotNull 검증을 통과했어야 한다" },
        throttleMinutes = requireNotNull(throttleMinutes) { "throttleMinutes 는 @NotNull 검증을 통과했어야 한다" },
        mutedCategories = mutedCategories.orEmpty().toSet(),
    )
}
