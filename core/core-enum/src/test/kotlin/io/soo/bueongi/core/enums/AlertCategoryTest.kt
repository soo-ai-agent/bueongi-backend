package io.soo.bueongi.core.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AlertCategoryTest {
    @Test
    fun `대상이 붙은 알림 key 는 콜론 앞부분으로 종류를 찾는다`() {
        val alertKey: String = AlertCategory.SAFETY_SPOT_FETCH_FAILED.keyOf("cctv")

        assertThat(AlertCategory.ofAlertKey(alertKey)).isEqualTo(AlertCategory.SAFETY_SPOT_FETCH_FAILED)
    }

    @Test
    fun `종류에 없는 알림 key 는 null 이다`() {
        assertThat(AlertCategory.ofAlertKey("tmap-route-fallback")).isNull()
    }
}
