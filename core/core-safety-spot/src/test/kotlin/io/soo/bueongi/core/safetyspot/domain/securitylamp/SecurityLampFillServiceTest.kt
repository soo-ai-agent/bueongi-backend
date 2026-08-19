package io.soo.bueongi.core.safetyspot.domain.securitylamp

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.client.datagokr.DataGoKrAddressRow
import io.soo.bueongi.client.datagokr.DataGoKrFetchResult
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.DataGoKrQuery
import io.soo.bueongi.client.kakao.KakaoAddressCoordClient
import io.soo.bueongi.client.kakao.KakaoAddressPoint
import io.soo.bueongi.client.datagokr.securitylamp.SecurityLampClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.securitylamp.service.SecurityLampFillEventPublisher
import io.soo.bueongi.core.safetyspot.domain.securitylamp.service.SecurityLampFillService
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SecurityLampFillServiceTest {

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
    fun `CSV 로 이미 적재된 자치단체는 API 를 부르지 않는다`(@TempDir dir: Path) {
        val client: SecurityLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)

        val added: Int = service(dir, client, FakeLiveStore(), alreadyLoaded = CHANGNYEONG).fill(CHANGNYEONG)

        assertThat(added).isZero()
        verify(client, never()).fetchWithAddressOnly(any())
    }

    @Test
    fun `주소만 온 지자체는 지오코딩으로 좌표를 살려 적재한다`(@TempDir dir: Path) {
        val client: SecurityLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchWithAddressOnly(any())).thenReturn(
            DataGoKrFetchResult(
                points = emptyList(),
                addressOnlyRows = listOf(
                    DataGoKrAddressRow(address = "서울 마포구 토정로37길 46", name = "도화동 07-08", sigungu = MAPO),
                    DataGoKrAddressRow(address = "서울 마포구 토정로37길 46", name = "도화동 07-09", sigungu = MAPO),
                ),
            ),
        )
        val geocoder: KakaoAddressCoordClient = mock()
        whenever(geocoder.isEnabled).thenReturn(true)
        whenever(geocoder.geocodeAll(any())).thenReturn(
            mapOf("서울 마포구 토정로37길 46" to KakaoAddressPoint(lat = 37.5445, lng = 126.9432)),
        )
        val liveStore = FakeLiveStore()

        val added: Int = service(dir, client, liveStore, alreadyLoaded = CHANGNYEONG, geocoder = geocoder).fill(MAPO)

        // 같은 주소의 등 2개가 모두 좌표를 얻는다 — 호출은 주소당 1번이지만 지점은 행마다 살린다.
        assertThat(added).isEqualTo(2)
        assertThat(liveStore.appended).allSatisfy { point ->
            assertThat(point.lat).isEqualTo(37.5445)
        }
    }

    @Test
    fun `지오코딩 배치가 실패하면 지역을 저장하지 않는다 - 부분 저장이 굳으면 안 된다`(@TempDir dir: Path) {
        val client: SecurityLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchWithAddressOnly(any())).thenReturn(
            DataGoKrFetchResult(
                points = emptyList(),
                addressOnlyRows = listOf(DataGoKrAddressRow(address = "서울 마포구 토정로37길 46")),
            ),
        )
        val geocoder: KakaoAddressCoordClient = mock()
        whenever(geocoder.isEnabled).thenReturn(true)
        whenever(geocoder.geocodeAll(any())).thenReturn(null)
        val liveStore = FakeLiveStore()
        val service: SecurityLampFillService =
            service(dir, client, liveStore, alreadyLoaded = CHANGNYEONG, geocoder = geocoder)

        assertThat(service.fill(MAPO)).isZero()
        assertThat(liveStore.appended).isEmpty()
        assertThat(service.isFilled(MAPO)).isFalse()
    }

    @Test
    fun `없는 자치단체는 받아서 인덱스에 더한다`(@TempDir dir: Path) {
        val client: SecurityLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetchWithAddressOnly(any())).thenReturn(
            DataGoKrFetchResult(
                points = listOf(DataGoKrPoint(lat = 37.52, lng = 126.81, name = "원종동 292-4", sigungu = BUCHEON)),
                addressOnlyRows = emptyList(),
            ),
        )
        val liveStore = FakeLiveStore()

        val added: Int = service(dir, client, liveStore, alreadyLoaded = CHANGNYEONG).fill(BUCHEON)

        assertThat(added).isEqualTo(1)
        assertThat(liveStore.appended.single().type).isEqualTo(SafetySpotType.SECURITY_LAMP)
        verify(client).fetchWithAddressOnly(any<DataGoKrQuery>())
    }

    private fun service(
        dir: Path,
        client: SecurityLampClient,
        liveStore: SafetySpotLiveStore,
        alreadyLoaded: String,
        geocoder: KakaoAddressCoordClient = mock(),
    ): SecurityLampFillService {
        val loaded = SafetySpotPoint(SafetySpotType.SECURITY_LAMP, 35.54, 128.49, sigungu = alreadyLoaded)
        val source: SafetySpotSpatialSource =
            FakeSafetySpotSource(mapOf(SafetySpotType.SECURITY_LAMP to listOf(loaded)))
        return SecurityLampFillService(
            client = client,
            geocoder = geocoder,
            cache = SafetySpotRegionCache(ObjectMapper(), dir.toString()),
            liveStore = liveStore,
            safetySpots = source,
            events = mock<SecurityLampFillEventPublisher>(),
        )
    }

    private companion object {
        const val CHANGNYEONG: String = "5410000"
        const val BUCHEON: String = "3860000"
        const val MAPO: String = "3130000"
    }
}
