package io.soo.bueongi.core.ops.domain.alert

import io.soo.bueongi.core.common.event.OperationEvent
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OperationEventListenerTest {

    private class RecordingNotifier : AlertNotifier {
        override val isEnabled: Boolean = true
        val sent: MutableList<Triple<String, String, String>> = ArrayList()
        override fun notify(key: String, title: String, detail: String) {
            sent.add(Triple(key, title, detail))
        }
    }

    @Test
    fun `사건의 key·title·detail 을 그대로 알림으로 넘긴다`() {
        val notifier = RecordingNotifier()

        OperationEventListener(notifier)
            .onOperationEvent(OperationEvent("tmap-route-fallback", "Tmap 경로 API 실패", "후보 0건"))

        assertThat(notifier.sent).containsExactly(
            Triple("tmap-route-fallback", "Tmap 경로 API 실패", "후보 0건"),
        )
    }

    @Test
    fun `같은 사건이라도 대상이 다르면 key 가 갈린다 - 스로틀이 서로를 삼키지 않게`() {
        val notifier = RecordingNotifier()
        val listener = OperationEventListener(notifier)

        listener.onOperationEvent(OperationEvent("missed-arrival:aaaaaa…", "미도착 의심 — 위치 공유", "1"))
        listener.onOperationEvent(OperationEvent("missed-arrival:bbbbbb…", "미도착 의심 — 위치 공유", "2"))

        assertThat(notifier.sent.map { it.first })
            .containsExactly("missed-arrival:aaaaaa…", "missed-arrival:bbbbbb…")
    }
}
