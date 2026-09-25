package io.soo.bueongi.core.safetyspot.domain.police

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPolicePoint
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.fill.service.NATIONWIDE
import io.soo.bueongi.core.safetyspot.domain.fill.service.SafetySpotFillEventPublisher
import io.soo.bueongi.core.safetyspot.domain.police.service.PoliceFillService
import io.soo.bueongi.storage.db.core.safetyspot.model.PoliceSpot
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

class PoliceFillServiceTest {
    private class FakeLiveStore : SafetySpotLiveStore {
        val appended: ArrayList<SafetySpotPoint> = ArrayList()

        override fun replace(type: SafetySpotType, points: List<SafetySpotPoint>): Int {
            appended.clear()
            appended.addAll(points)
            return points.size
        }

        override fun append(type: SafetySpotType, points: List<SafetySpotPoint>): Int {
            appended.addAll(points)
            return points.size
        }
    }

    @Test
    fun `키가 없으면 외부 호출을 하지 않는다`(
        @TempDir dir: Path,
    ) {
        val client: SafemapPoliceClient = mock()
        whenever(client.isEnabled).thenReturn(false)
        val liveStore = FakeLiveStore()

        val added: Int = service(dir, client, liveStore).fill()

        assertThat(added).isZero()
        assertThat(liveStore.appended).isEmpty()
        verify(client, never()).fetchPoliceFacilities()
    }

    @Test
    fun `받은 치안시설을 인덱스에 더하고 다시 받지 않는다`(
        @TempDir dir: Path,
    ) {
        val client: SafemapPoliceClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchPoliceFacilities()).thenReturn(
            listOf(
                SafemapPolicePoint(
                    lat = 37.4981,
                    lng = 127.0276,
                    name = "역삼지구대",
                    address = "서울 강남구 테헤란로 1",
                    phone = "02-000-0000",
                    sigungu = "11680",
                ),
            ),
        )
        val liveStore = FakeLiveStore()
        val service: PoliceFillService = service(dir, client, liveStore)

        assertThat(service.fill()).isEqualTo(1)
        val stored: PoliceSpot = liveStore.appended.single() as PoliceSpot
        assertThat(stored.name).isEqualTo("역삼지구대")
        assertThat(stored.phone).isEqualTo("02-000-0000")

        assertThat(service.fill()).isZero()
        verify(client).fetchPoliceFacilities()
    }

    @Test
    fun `수집이 실패하면 알리고 기존 데이터를 유지한다`(
        @TempDir dir: Path,
    ) {
        val client: SafemapPoliceClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchPoliceFacilities()).thenReturn(null)
        val liveStore = FakeLiveStore()
        val events: SafetySpotFillEventPublisher = mock()

        val added: Int = service(dir, client, liveStore, events).fill()

        assertThat(added).isZero()
        assertThat(liveStore.appended).isEmpty()
        verify(events).fetchFailed(SafetySpotType.POLICE, NATIONWIDE)
        verify(events, never()).hadNoCoordinates(any(), any(), any())
    }

    private fun service(
        dir: Path,
        client: SafemapPoliceClient,
        liveStore: SafetySpotLiveStore,
        events: SafetySpotFillEventPublisher = mock(),
    ): PoliceFillService =
        PoliceFillService(
            client = client,
            cache = SafetySpotRegionCache(ObjectMapper(), dir.toString()),
            liveStore = liveStore,
            events = events,
        )
}
