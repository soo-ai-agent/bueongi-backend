package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.common.event.SafetySpotFetchPerformed
import io.soo.bueongi.core.enums.SafetySpotType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetySpotFetchEventListenerTest {
    @Test
    fun `수집 기록 이벤트를 받으면 장부에 적는다`() {
        val ledger = SafetySpotFetchLedger()

        SafetySpotFetchEventListener(ledger)
            .onFetchPerformed(SafetySpotFetchPerformed(SafetySpotType.STREET_LAMP, "nationwide", receivedCount = 33_245))

        val status: SafetySpotFetchStatus = ledger.findAll().single()
        assertThat(status.type).isEqualTo(SafetySpotType.STREET_LAMP)
        assertThat(status.successCount).isEqualTo(1)
        assertThat(status.lastReceivedCount).isEqualTo(33_245)
    }
}
