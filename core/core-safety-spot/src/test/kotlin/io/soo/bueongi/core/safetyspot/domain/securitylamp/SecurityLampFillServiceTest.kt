package io.soo.bueongi.core.safetyspot.domain.securitylamp

import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.datagokr.securitylamp.SecurityLampClient
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.domain.securitylamp.service.SecurityLampFillService
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotLiveStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRegionCache
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import java.nio.file.Path
import java.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SecurityLampFillServiceTest {
    @Test
    fun `CSV 로 이미 적재된 자치단체는 API 를 부르지 않는다`(
        @TempDir dir: Path,
    ) {
        val client: SecurityLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)

        val added: Int = service(dir, client, FakeLiveStore(), alreadyLoaded = CHANGNYEONG).fill(CHANGNYEONG)

        assertThat(added).isZero()
        verify(client, never()).fetch(any())
    }

    @Test
    fun `없는 자치단체는 받아서 인덱스에 더한다`(
        @TempDir dir: Path,
    ) {
        val client: SecurityLampClient = mock()
        whenever(client.isEnabled).thenReturn(true)
        whenever(client.fetch(any())).thenReturn(
            listOf(DataGoKrPoint(lat = 37.52, lng = 126.81, name = "원종동 292-4", sigungu = BUCHEON)),
        )
        val liveStore = FakeLiveStore()

        val added: Int = service(dir, client, liveStore, alreadyLoaded = CHANGNYEONG).fill(BUCHEON)

        assertThat(added).isEqualTo(1)
        assertThat(liveStore.appended.single().type).isEqualTo(SafetySpotType.SECURITY_LAMP)
        verify(client).fetch(any<String>())
    }

    private fun service(
        dir: Path,
        client: SecurityLampClient,
        liveStore: SafetySpotLiveStore,
        alreadyLoaded: String,
    ): SecurityLampFillService {
        val loaded = SafetySpotPoint.of(SafetySpotType.SECURITY_LAMP, 35.54, 128.49, regionCode = alreadyLoaded)
        val source: SafetySpotSpatialSource =
            FakeSafetySpotSource(mapOf(SafetySpotType.SECURITY_LAMP to listOf(loaded)))
        return SecurityLampFillService(
            client = client,
            cache = SafetySpotRegionCache(liveStore, 90L, Clock.systemUTC()),
            liveStore = liveStore,
            safetySpots = source,
        )
    }

    private companion object {
        const val CHANGNYEONG: String = "5410000"
        const val BUCHEON: String = "3860000"
    }
}
