package io.soo.bueongi.core.domain.admin

import io.soo.bueongi.core.support.error.InvalidSlackCategoryException
import org.springframework.stereotype.Service

data class SlackConfigSummary(
    val webhookConfigured: Boolean,
    val webhookMasked: String?,
    val enabled: Boolean,
    val throttleMinutes: Int,
    val categories: List<SlackCategoryConfig>,
)
