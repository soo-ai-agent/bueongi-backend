package io.soo.bueongi.core.domain.admin.slack

data class SlackConfigUpdate(
    val enabled: Boolean,
    val throttleMinutes: Int,
    val mutedCategories: Set<String>,
)
