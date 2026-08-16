package io.soo.bueongi.core.ops.domain.slack.model

data class SlackConfigSummary(
    val webhookConfigured: Boolean,
    // 웹훅을 설정하지 않으면 null 이 가능함
    val webhookMasked: String?,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
