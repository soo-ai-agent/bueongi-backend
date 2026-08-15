package io.soo.bueongi.core.domain.admin.slack

import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig

import io.soo.bueongi.core.support.error.InvalidSlackCategoryException
import org.springframework.stereotype.Service

@Service
class SlackConfigService(
    private val slackAlertConfig: SlackAlertConfig,
    private val alertNotifier: AlertNotifier,
) {

    fun view(): SlackConfigSummary {
        val muted: Set<String> = slackAlertConfig.mutedCategories()
        val categories: List<SlackCategoryConfig> = KNOWN_CATEGORIES.map { (key, label) ->
            SlackCategoryConfig(key = key, label = label, muted = muted.contains(key))
        }
        return SlackConfigSummary(
            webhookConfigured = slackAlertConfig.webhookConfigured(),
            webhookMasked = slackAlertConfig.maskedWebhook(),
            enabled = slackAlertConfig.enabled(),
            throttleMinutes = slackAlertConfig.throttleMinutes(),
            categories = categories,
        )
    }

    fun update(update: SlackConfigUpdate): SlackConfigSummary {
        rejectUnknownCategories(update.mutedCategories)
        slackAlertConfig.update(
            enabled = update.enabled,
            throttleMinutes = update.throttleMinutes,
            mutedCategories = update.mutedCategories,
        )
        return view()
    }

    fun test(): SlackTestResult {
        val sent: Boolean = alertNotifier.sendTest()
        return SlackTestResult(sent = sent, enabled = slackAlertConfig.sendEnabled())
    }

    private fun rejectUnknownCategories(categories: Set<String>) {
        val unknown: Set<String> = categories - KNOWN_CATEGORIES.keys
        if (unknown.isNotEmpty()) {
            throw InvalidSlackCategoryException(
                rejectedValue = unknown.sorted(),
                knownKeys = KNOWN_CATEGORIES.keys.toList(),
            )
        }
    }

    companion object {
        val KNOWN_CATEGORIES: Map<String, String> = linkedMapOf(
            "api-500" to "서버 오류(미처리 500)",
            "kakao-place-fallback" to "장소검색 폴백(Kakao)",
            "tmap-route-fallback" to "경로 폴백(Tmap)",
            "facility-index-empty" to "안심시설 데이터 없음",
            "facility-refresh" to "시설 라이브 갱신 실패",
        )
    }
}
