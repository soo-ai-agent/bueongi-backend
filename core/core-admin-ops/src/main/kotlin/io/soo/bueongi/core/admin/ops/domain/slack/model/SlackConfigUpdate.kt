package io.soo.bueongi.core.admin.ops.domain.slack.model

data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)
