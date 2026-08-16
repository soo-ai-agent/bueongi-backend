package io.soo.bueongi.core.ops.domain.slack.model

/** 웹훅을 설정하지 않았으면 비어 있다. */
data class SlackConfigSummary(
    val webhookConfigured: Boolean,
    val webhookMasked: String?,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
