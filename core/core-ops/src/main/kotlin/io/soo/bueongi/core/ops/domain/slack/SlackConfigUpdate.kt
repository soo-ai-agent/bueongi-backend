package io.soo.bueongi.core.ops.domain.slack

data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)
