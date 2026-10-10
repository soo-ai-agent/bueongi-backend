package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackCategoryConfig
import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackConfigSummary

data class SlackConfigResponse(
    val webhookConfigured: Boolean,
    val webhookMasked: String,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
) {
    companion object {
        fun from(summary: SlackConfigSummary): SlackConfigResponse {
            return SlackConfigResponse(
                webhookConfigured = summary.webhookConfigured,
                webhookMasked = summary.webhookMasked,
                enabled = summary.enabled,
                throttleMinutes = summary.throttleMinutes,
                categories = summary.categories,
            )
        }
    }
}
