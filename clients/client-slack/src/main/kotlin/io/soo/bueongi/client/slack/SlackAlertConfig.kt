package io.soo.bueongi.client.slack

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SlackAlertConfig(
    @Value("\${bueongi.alert.slack-webhook-url:}") webhookUrl: String,
    @Value("\${bueongi.alert.throttle-minutes:10}") throttleMinutes: Int,
) {
    private val webhook: String = webhookUrl.trim()

    @Volatile
    private var masterEnabled: Boolean = webhook.isNotEmpty()

    @Volatile
    private var throttleMinutesValue: Int = throttleMinutes.coerceIn(MIN_THROTTLE_MINUTES, MAX_THROTTLE_MINUTES)

    @Volatile
    private var mutedCategorySet: Set<String> = emptySet()

    fun webhookConfigured(): Boolean = webhook.isNotEmpty()

    fun enabled(): Boolean = masterEnabled

    fun throttleMinutes(): Int = throttleMinutesValue

    fun mutedCategories(): Set<String> = mutedCategorySet

    fun sendEnabled(): Boolean = webhookConfigured() && masterEnabled

    fun throttleMillis(): Long = throttleMinutesValue.toLong() * MILLIS_PER_MINUTE

    // 발송 전용 원문 — 응답·로그·예외 어디에도 노출 금지.
    fun webhookUrl(): String = webhook

    fun isMuted(key: String): Boolean {
        val category: String = key.substringBefore(DELIMITER)
        return mutedCategorySet.contains(category)
    }

    fun maskedWebhook(): String {
        if (webhook.isEmpty()) {
            return ""
        }
        return maskWebhook(webhook)
    }

    fun update(enabled: Boolean, throttleMinutes: Int, mutedCategories: Set<String>) {
        require(throttleMinutes in MIN_THROTTLE_MINUTES..MAX_THROTTLE_MINUTES) {
            "throttleMinutes 는 $MIN_THROTTLE_MINUTES..$MAX_THROTTLE_MINUTES 여야 합니다."
        }
        masterEnabled = enabled
        throttleMinutesValue = throttleMinutes
        mutedCategorySet = mutedCategories.toSet()
    }

    private fun maskWebhook(url: String): String {
        val markerIndex: Int = url.indexOf(SERVICES_MARKER)
        if (markerIndex < 0) {
            return conservativeMask(url)
        }
        val prefix: String = url.take(markerIndex + SERVICES_MARKER.length)
        val segments: List<String> =
            url.drop(markerIndex + SERVICES_MARKER.length)
                .split("/")
                .filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            return conservativeMask(url)
        }
        val maskedSegments: List<String> =
            segments.mapIndexed { index, segment ->
                if (index == segments.lastIndex) {
                    MASK
                } else {
                    segment.take(1) + MASK
                }
            }
        return prefix + maskedSegments.joinToString("/")
    }

    private fun conservativeMask(url: String): String = url.take(CONSERVATIVE_PREFIX_LENGTH) + MASK

    companion object {
        // 요청 DTO 의 @Min/@Max 와 같은 경계를 유지해야 한다.
        const val MIN_THROTTLE_MINUTES: Int = 1
        const val MAX_THROTTLE_MINUTES: Int = 1440

        private const val MILLIS_PER_MINUTE: Long = 60_000L
        private const val DELIMITER: String = ":"
        private const val SERVICES_MARKER: String = "/services/"
        private const val MASK: String = "***"
        private const val CONSERVATIVE_PREFIX_LENGTH: Int = 8
    }
}
