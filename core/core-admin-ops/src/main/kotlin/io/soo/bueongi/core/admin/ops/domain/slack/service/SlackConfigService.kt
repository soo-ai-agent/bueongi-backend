package io.soo.bueongi.core.admin.ops.domain.slack.service

import io.soo.bueongi.client.slack.SlackAlertConfig
import io.soo.bueongi.core.admin.ops.api.response.SlackConfigResponse
import io.soo.bueongi.core.admin.ops.api.response.SlackTestResponse
import io.soo.bueongi.core.admin.ops.domain.error.InvalidSlackCategoryException
import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackCategoryConfig
import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackConfigSummary
import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackConfigUpdate
import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.springframework.stereotype.Service

@Service
class SlackConfigService(
    private val slackAlertConfig: SlackAlertConfig,
    private val alertNotifier: AlertNotifier,
) {
    fun view(): SlackConfigResponse {
        val muted: Set<String> = slackAlertConfig.mutedCategories()
        val categories: ArrayList<SlackCategoryConfig> = ArrayList()
        for (category in AlertCategory.entries) {
            categories.add(SlackCategoryConfig(key = category.key, label = category.label, muted = muted.contains(category.key)))
        }
        val summary: SlackConfigSummary =
            SlackConfigSummary(
                webhookConfigured = slackAlertConfig.webhookConfigured(),
                webhookMasked = slackAlertConfig.maskedWebhook(),
                enabled = slackAlertConfig.enabled(),
                throttleMinutes = slackAlertConfig.throttleMinutes(),
                categories = categories,
            )
        return SlackConfigResponse.from(summary)
    }

    fun update(update: SlackConfigUpdate): SlackConfigResponse {
        val knownKeys: ArrayList<String> = ArrayList()
        for (category in AlertCategory.entries) {
            knownKeys.add(category.key)
        }
        val unknown: Set<String> = update.mutedCategories - knownKeys.toSet()
        if (unknown.isNotEmpty()) {
            throw InvalidSlackCategoryException(
                rejectedValue = unknown.sorted(),
                knownKeys = knownKeys,
            )
        }
        slackAlertConfig.update(
            enabled = update.enabled,
            throttleMinutes = update.throttleMinutes,
            mutedCategories = update.mutedCategories,
        )
        return view()
    }

    fun test(): SlackTestResponse {
        val sent: Boolean = alertNotifier.sendTest()
        val enabled: Boolean = slackAlertConfig.sendEnabled()
        return SlackTestResponse.from(sent, enabled)
    }
}
