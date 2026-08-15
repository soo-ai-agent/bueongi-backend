package io.soo.bueongi.core.ops.domain.slack

data class SlackConfigSummary(
    val webhookConfigured: Boolean,
    val webhookMasked: String?,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
