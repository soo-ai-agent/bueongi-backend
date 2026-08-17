package io.soo.bueongi.core.safetyspot.domain.streetlight

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.safetydata.SafetydataStreetlight
import io.soo.bueongi.client.safetydata.SafetydataStreetlightClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.streetlight.service.StreetlightFillEventPublisher
import io.soo.bueongi.core.safetyspot.domain.streetlight.service.StreetlightFillService
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StreetlightFillServiceTest {

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
    fun `키가 없으면 외부 호출을 하지 않는다`(@TempDir dir: Path) {
        val client: SafetydataStreetlightClient = mock()
        whenever(client.isEnabled).thenReturn(false)
        val liveStore = FakeLiveStore()

        val added: Int = service(dir, client, liveStore).fill()

        assertThat(added).isZero()
        assertThat(liveStore.appended).isEmpty()
        verify(client, never()).fetchStreetlights()
    }

    @Test
    fun `받은 가로등을 인덱스에 더하고 다시 받지 않는다`(@TempDir dir: Path) {
        val client: SafetydataStreetlightClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchStreetlights()).thenReturn(
            listOf(
                SafetydataStreetlight(lat = 37.4979, lng = 127.0276, address = "역삼동", sigungu = "11680"),
                SafetydataStreetlight(lat = 37.4988, lng = 127.0301),
            ),
        )
        val liveStore = FakeLiveStore()
        val service: StreetlightFillService = service(dir, client, liveStore)

        assertThat(service.fill()).isEqualTo(2)
        assertThat(liveStore.appended).hasSize(2)
        assertThat(liveStore.appended.first().type).isEqualTo(SafetySpotType.LAMP)
        assertThat(liveStore.appended.first().address).isEqualTo("역삼동")

        // 두 번째 호출은 캐시가 막는다 — 같은 데이터를 두 번 받지 않는다.
        assertThat(service.fill()).isZero()
        assertThat(liveStore.appended).hasSize(2)
        verify(client).fetchStreetlights()
    }

    @Test
    fun `수집이 실패하면 알리고 기존 데이터를 유지한다`(@TempDir dir: Path) {
        val client: SafetydataStreetlightClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchStreetlights()).thenReturn(null)
        val liveStore = FakeLiveStore()
        val events: StreetlightFillEventPublisher = mock()

        val added: Int = service(dir, client, liveStore, events).fill()

        assertThat(added).isZero()
        assertThat(liveStore.appended).isEmpty()
        verify(events).fetchFailed()
        // 실패는 캐시로 남기지 않는다 — 다음 요청에서 다시 시도해야 한다.
        verify(events, never()).hadNoCoordinates(any())
    }

    private fun service(
        dir: Path,
        client: SafetydataStreetlightClient,
        liveStore: SafetySpotLiveStore,
        events: StreetlightFillEventPublisher = mock(),
    ): StreetlightFillService =
        StreetlightFillService(
            client = client,
            cache = SafetySpotRegionCache(ObjectMapper(), dir.toString()),
            liveStore = liveStore,
            events = events,
        )
}
