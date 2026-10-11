package io.soo.bueongi.core.admin.ops.api.response

import io.soo.bueongi.core.enums.AlertCategory
import io.soo.bueongi.core.enums.AlertSeverity
import io.soo.bueongi.support.monitoring.AlertRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AlertResponseTest {
    @Test
    fun `알림 key 의 종류로 이름과 심각도를 싣는다`() {
        val key: String = AlertCategory.MISSED_ARRIVAL.keyOf("abc123…")

        val response: AlertResponse = AlertResponse.from(AlertRecord(atEpochMs = 1L, key = key, title = "t", detail = "d"))

        assertThat(response.category).isEqualTo("missed-arrival")
        assertThat(response.label).isEqualTo("미도착 의심")
        assertThat(response.severity).isEqualTo(AlertSeverity.CRITICAL)
    }

    @Test
    fun `종류를 모르는 key 는 콜론 앞부분을 이름으로 쓰고 심각도는 unknown 이다`() {
        val response: AlertResponse = AlertResponse.from(AlertRecord(atEpochMs = 1L, key = "mystery:x", title = "t", detail = "d"))

        assertThat(response.category).isEqualTo("mystery")
        assertThat(response.label).isEqualTo("mystery")
        assertThat(response.severity).isEqualTo(AlertSeverity.UNKNOWN)
    }
}
