package io.soo.bueongi.core.safetyspot.domain.streetlamp

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.safetydata.streetlamp.StreetLampClient
import io.soo.bueongi.client.safetydata.streetlamp.StreetLampPoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.model.FillFailureCooldown
import io.soo.bueongi.core.safetyspot.domain.fill.service.NATIONWIDE
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.core.safetyspot.domain.streetlamp.service.StreetLampFillService
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class StreetLampFillServiceTest {
    private class FakeLiveStore : SafetySpotLiveStore {
        val appended: ArrayList<SafetySpotPoint> = ArrayList()

        override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
            appended.clear()
            appended.addAll(points)
        }

        override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
            appended.addAll(points)
        }
    }

    @Test
    fun `키가 없으면 외부 호출을 하지 않는다`(
        @TempDir dir: Path,
    ) {
        val client: StreetLampClient = mock()
        whenever(client.isEnabled).thenReturn(false)
        val liveStore = FakeLiveStore()

        val added: Int = service(dir, client, liveStore).fill()

        assertThat(added).isZero()
        assertThat(liveStore.appended).isEmpty()
        verify(client, never()).fetch()
    }

    @Test
    fun `받은 가로등을 인덱스에 더하고 다시 받지 않는다`(
        @TempDir dir: Path,
    ) {
        val client: StreetLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetch()).thenReturn(
            listOf(
                StreetLampPoint(lat = 37.4979, lng = 127.0276, address = "역삼동", sigungu = "11680"),
                StreetLampPoint(lat = 37.4988, lng = 127.0301),
            ),
        )
        val liveStore = FakeLiveStore()
        val service: StreetLampFillService = service(dir, client, liveStore)

        assertThat(service.fill()).isEqualTo(2)
        assertThat(liveStore.appended).hasSize(2)
        assertThat(liveStore.appended.first().type).isEqualTo(SafetySpotType.STREET_LAMP)
        assertThat(liveStore.appended.first().address).isEqualTo("역삼동")

        assertThat(service.fill()).isZero()
        assertThat(liveStore.appended).hasSize(2)
        verify(client).fetch()
    }

    @Test
    fun `실패 직후에는 다시 외부를 부르지 않고 냉각이 지나면 다시 시도한다`(
        @TempDir dir: Path,
    ) {
        val client: StreetLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetch()).thenReturn(null)
        val now: Instant = Instant.parse("2026-08-19T00:00:00Z")
        val ticking = TickingClock(now)
        val service: StreetLampFillService =
            StreetLampFillService(
                client = client,
                cache = SafetySpotRegionCache(ObjectMapper(), dir.toString()),
                liveStore = FakeLiveStore(),
                retryGate = FillFailureCooldown(cooldown = Duration.ofMinutes(10), clock = ticking),
                events = mock(),
            )

        service.fill()
        service.fill()
        verify(client, times(1)).fetch()

        ticking.now = now.plus(Duration.ofMinutes(11))
        service.fill()
        verify(client, times(2)).fetch()
    }

    private class TickingClock(
        var now: Instant,
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this
    }

    @Test
    fun `수집이 실패하면 알리고 기존 데이터를 유지한다`(
        @TempDir dir: Path,
    ) {
        val client: StreetLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetch()).thenReturn(null)
        val liveStore = FakeLiveStore()
        val events: SafetySpotFillEventPublisher = mock()

        val added: Int = service(dir, client, liveStore, events).fill()

        assertThat(added).isZero()
        assertThat(liveStore.appended).isEmpty()
        verify(events).fetchFailed(SafetySpotType.STREET_LAMP, NATIONWIDE)
        verify(events, never()).hadNoCoordinates(any(), any(), any())
    }

    private fun service(
        dir: Path,
        client: StreetLampClient,
        liveStore: SafetySpotLiveStore,
        events: SafetySpotFillEventPublisher = mock(),
    ): StreetLampFillService =
        StreetLampFillService(
            client = client,
            cache = SafetySpotRegionCache(ObjectMapper(), dir.toString()),
            liveStore = liveStore,
            events = events,
        )
}
