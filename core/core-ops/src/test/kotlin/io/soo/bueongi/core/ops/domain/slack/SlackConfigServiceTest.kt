package io.soo.bueongi.core.ops.domain.slack

import io.soo.bueongi.core.ops.domain.slack.service.SlackConfigService
import io.soo.bueongi.core.ops.domain.slack.model.SlackConfigUpdate

import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.SlackAlertConfig

import io.soo.bueongi.core.ops.domain.error.InvalidSlackCategoryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SlackConfigServiceTest {

    private val webhook: String = "https://hooks.slack.com/services/T00000000/B11111111/abcdefghijklmnop"

    private fun notifier(sendTestResult: Boolean = false): AlertNotifier = object : AlertNotifier {
        override val isEnabled: Boolean = false
        override fun notify(key: String, title: String, detail: String) = Unit
        override fun sendTest(): Boolean = sendTestResult
    }

    private fun service(
        url: String = webhook,
        throttleMinutes: Int = 10,
        sendTestResult: Boolean = false,
    ): Pair<SlackConfigService, SlackAlertConfig> {
        val config = SlackAlertConfig(webhookUrl = url, throttleMinutes = throttleMinutes)
        return SlackConfigService(config, notifier(sendTestResult)) to config
    }

    @Test
    fun `view - webhook 은 경로 토큰만 마스킹하고 원문은 노출하지 않는다`() {
        val (svc, _) = service()

        val summary = svc.view()

        assertThat(summary.webhookConfigured).isTrue()
        assertThat(summary.webhookMasked).isEqualTo("https://hooks.slack.com/services/T***/B***/***")
        assertThat(summary.webhookMasked).doesNotContain("T00000000")
        assertThat(summary.webhookMasked).doesNotContain("abcdefghijklmnop")
        assertThat(summary.enabled).isTrue()
        assertThat(summary.throttleMinutes).isEqualTo(10)
    }

    @Test
    fun `view - 카테고리는 고정 5종 라벨을 순서대로 담고 기본은 뮤트 아님`() {
        val (svc, _) = service()

        val categories = svc.view().categories

        assertThat(categories.map { it.key }).containsExactly(
            "api-500", "kakao-place-fallback", "tmap-route-fallback", "safetySpot-index-empty", "safetySpot-refresh",
        )
        assertThat(categories.first { it.key == "api-500" }.label).isEqualTo("서버 오류(미처리 500)")
        assertThat(categories.all { !it.muted }).isTrue()
    }

    @Test
    fun `view - webhook 미설정이면 configured false 이고 마스킹은 null, enabled false`() {
        val (svc, _) = service(url = "")

        val summary = svc.view()

        assertThat(summary.webhookConfigured).isFalse()
        assertThat(summary.webhookMasked).isNull()
        assertThat(summary.enabled).isFalse()
    }

    @Test
    fun `update - 토글·스로틀·뮤트를 반영한 요약을 돌려준다`() {
        val (svc, config) = service()

        val updated = svc.update(
            SlackConfigUpdate(
                enabled = false,
                throttleMinutes = 30,
                mutedCategories = setOf("api-500", "safetySpot-refresh"),
            ),
        )

        assertThat(updated.enabled).isFalse()
        assertThat(updated.throttleMinutes).isEqualTo(30)
        assertThat(updated.categories.first { it.key == "api-500" }.muted).isTrue()
        assertThat(updated.categories.first { it.key == "safetySpot-refresh" }.muted).isTrue()
        assertThat(updated.categories.first { it.key == "tmap-route-fallback" }.muted).isFalse()
        assertThat(config.isMuted("api-500:NullPointerException")).isTrue()
        assertThat(config.sendEnabled()).isFalse()
    }

    @Test
    fun `update - 알 수 없는 카테고리는 거부하고 기존 설정을 바꾸지 않는다`() {
        val (svc, config) = service()

        assertThatThrownBy {
            svc.update(
                SlackConfigUpdate(enabled = true, throttleMinutes = 10, mutedCategories = setOf("api-500", "bogus")),
            )
        }.isInstanceOf(InvalidSlackCategoryException::class.java)

        assertThat(config.mutedCategories()).isEmpty()
    }

    @Test
    fun `test - 알림기의 sendTest 결과와 현재 효과 발송 여부를 함께 돌려준다`() {
        val (svc, _) = service(sendTestResult = true)

        val result = svc.test()

        assertThat(result.sent).isTrue()
        assertThat(result.enabled).isTrue()
    }
}
