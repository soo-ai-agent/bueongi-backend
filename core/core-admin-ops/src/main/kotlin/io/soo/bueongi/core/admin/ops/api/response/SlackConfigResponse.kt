package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackCategoryConfig

data class SlackConfigResponse(
    val webhookConfigured: Boolean,
    val webhookMasked: String,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
