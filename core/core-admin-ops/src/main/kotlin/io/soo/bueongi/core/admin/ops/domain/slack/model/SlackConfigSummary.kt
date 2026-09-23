package io.soo.bueongi.core.admin.ops.domain.slack.model

data class SlackConfigSummary(
    val webhookConfigured: Boolean,
    val webhookMasked: String,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
