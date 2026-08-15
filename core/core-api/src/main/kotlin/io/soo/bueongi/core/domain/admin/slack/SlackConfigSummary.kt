package io.soo.bueongi.core.domain.admin.slack

data class SlackConfigSummary(
    val webhookConfigured: Boolean,
    val webhookMasked: String?,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
