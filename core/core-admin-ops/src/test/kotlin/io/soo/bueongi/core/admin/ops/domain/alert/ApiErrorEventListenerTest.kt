package io.soo.bueongi.core.admin.ops.domain.alert

import io.soo.bueongi.core.common.event.ApiErrorResponded
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiErrorEventListenerTest {
    private class RecordingNotifier : AlertNotifier {
        override val isEnabled: Boolean = true
        val sent: MutableList<Triple<String, String, String>> = ArrayList()

        override fun notify(key: String, title: String, detail: String) {
            sent.add(Triple(key, title, detail))
        }
    }

    @Test
    fun `경로 탐색 불가 응답이면 Tmap 경로 실패 알림을 보낸다`() {
        val notifier = RecordingNotifier()

        ApiErrorEventListener(notifier)
            .onApiErrorResponded(ApiErrorResponded(code = "ROUTE_SEARCH_UNAVAILABLE", causeName = null))

        assertThat(notifier.sent).hasSize(1)
        assertThat(notifier.sent[0].first).isEqualTo("tmap-route-failed")
        assertThat(notifier.sent[0].second).isEqualTo("Tmap 경로 API 실패")
    }

    @Test
    fun `장소 검색 불가 응답이면 원인 예외 이름을 실어 알림을 보낸다`() {
        val notifier = RecordingNotifier()

        ApiErrorEventListener(notifier)
            .onApiErrorResponded(ApiErrorResponded(code = "PLACE_SEARCH_UNAVAILABLE", causeName = "HttpClientErrorException"))

        assertThat(notifier.sent).hasSize(1)
        assertThat(notifier.sent[0].first).isEqualTo("tmap-place-failed")
        assertThat(notifier.sent[0].third).isEqualTo("HttpClientErrorException")
    }

    @Test
    fun `장소 검색 불가인데 원인이 없으면 원인 미상으로 보낸다`() {
        val notifier = RecordingNotifier()

        ApiErrorEventListener(notifier)
            .onApiErrorResponded(ApiErrorResponded(code = "PLACE_SEARCH_UNAVAILABLE", causeName = null))

        assertThat(notifier.sent[0].third).isEqualTo("원인 미상")
    }

    @Test
    fun `알림 대상이 아닌 code 는 알리지 않는다`() {
        val notifier = RecordingNotifier()

        ApiErrorEventListener(notifier)
            .onApiErrorResponded(ApiErrorResponded(code = "NOT_FOUND", causeName = null))

        assertThat(notifier.sent).isEmpty()
    }
}
