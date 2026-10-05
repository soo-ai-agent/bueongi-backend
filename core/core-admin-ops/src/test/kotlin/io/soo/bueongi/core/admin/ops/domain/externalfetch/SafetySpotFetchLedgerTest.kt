package io.soo.bueongi.core.admin.ops.domain.externalfetch

import io.soo.bueongi.core.common.event.SafetySpotFetchPerformed
import io.soo.bueongi.core.enums.SafetySpotType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SafetySpotFetchLedgerTest {
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @Test
    fun `성공과 실패를 종류별로 따로 센다`() {
        val ledger = SafetySpotFetchLedger(clock)

        ledger.record(SafetySpotFetchPerformed(SafetySpotType.SECURITY_LAMP, "3860000", receivedCount = 120))
        ledger.record(SafetySpotFetchPerformed(SafetySpotType.SECURITY_LAMP, "5410000", receivedCount = null))
        ledger.record(SafetySpotFetchPerformed(SafetySpotType.POLICE, "nationwide", receivedCount = 2047))

        val lamp: SafetySpotFetchStatus = ledger.statuses().single { it.type == SafetySpotType.SECURITY_LAMP }
        assertThat(lamp.successCount).isEqualTo(1)
        assertThat(lamp.failureCount).isEqualTo(1)
        val police: SafetySpotFetchStatus = ledger.statuses().single { it.type == SafetySpotType.POLICE }
        assertThat(police.successCount).isEqualTo(1)
        assertThat(police.failureCount).isZero()
    }

    @Test
    fun `마지막 호출의 시각·범위·받은 건수를 덮어쓴다`() {
        val ledger = SafetySpotFetchLedger(clock)

        ledger.record(SafetySpotFetchPerformed(SafetySpotType.SAFEHOUSE, "tile-11", receivedCount = 3))
        ledger.record(SafetySpotFetchPerformed(SafetySpotType.SAFEHOUSE, "tile-12", receivedCount = null))

        val status: SafetySpotFetchStatus = ledger.statuses().single()
        assertThat(status.lastAtMs).isEqualTo(1_000)
        assertThat(status.lastScope).isEqualTo("tile-12")
        assertThat(status.lastReceivedCount).describedAs("실패면 받은 건수가 없다").isNull()
    }
}
