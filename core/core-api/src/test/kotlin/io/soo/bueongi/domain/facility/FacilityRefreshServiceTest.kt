package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.datagokr.DataGoKrFacilityClient
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPoliceFacility
import io.soo.bueongi.client.safemap.SafetydataStreetlight
import io.soo.bueongi.client.safemap.SafetydataStreetlightClient
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FacilityRefreshServiceTest {

    private class FakeStore : FacilityLiveStore {
        val replaced = LinkedHashMap<FacilityType, List<FacilityPoint>>()
        override fun replace(type: FacilityType, points: List<FacilityPoint>): Int {
            replaced[type] = points
            return points.size
        }
    }

    private class RecordingNotifier : AlertNotifier {
        val keys: ArrayList<String> = ArrayList<String>()
        override val isEnabled: Boolean = true
        override fun notify(key: String, title: String, detail: String) {
            keys.add(key)
        }
    }

    private val dataGoKr: DataGoKrFacilityClient = mock()
    private val safemap: SafemapPoliceClient = mock()
    private val streetlight: SafetydataStreetlightClient = mock()
    private val store: FakeStore = FakeStore()
    private val notifier: RecordingNotifier = RecordingNotifier()
    private val service: FacilityRefreshService =
        FacilityRefreshService(dataGoKr, safemap, streetlight, store, notifier)

    private fun enableDataGoKr() = whenever(dataGoKr.isEnabled).thenReturn(true)
    private fun enableSafemap() = whenever(safemap.isEnabled).thenReturn(true)
    private fun enableStreetlight() = whenever(streetlight.isEnabled).thenReturn(true)

    @Test
    fun `정상 - 두 원천의 수집분을 도메인 모델로 매핑해 종류별 인덱스를 교체한다`() {
        enableDataGoKr()
        enableSafemap()
        enableStreetlight()
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(
                DataGoKrPoint(lat = 37.51, lng = 127.02, name = "강남구청", sigungu = "11680"),
                DataGoKrPoint(lat = 37.52, lng = 127.03, name = "서초구청"),
            ),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.498, lng = 127.027, name = "GS25 강남점", address = "강남대로 396")),
        )
        whenever(streetlight.fetchStreetlights()).thenReturn(
            listOf(SafetydataStreetlight(lat = 37.53, lng = 127.04, sigungu = "11680")),
        )
        whenever(safemap.fetchPoliceFacilities()).thenReturn(
            listOf(
                SafemapPoliceFacility(
                    lat = 37.4981, lng = 127.0276,
                    name = "역삼지구대", address = "테헤란로 1", phone = "02-123-4567", sigungu = "11680",
                ),
            ),
        )

        val result = service.refreshAll()

        assertThat(result).doesNotContainKey(FacilityType.CCTV)
        assertThat(result[FacilityType.BELL]).isEqualTo(2)
        assertThat(result[FacilityType.SAFEHOUSE]).isEqualTo(1)
        assertThat(result[FacilityType.LAMP]).isEqualTo(1)
        assertThat(result[FacilityType.POLICE]).isEqualTo(1)
        assertThat(store.replaced[FacilityType.LAMP]!![0].sigungu).isEqualTo("11680")
        assertThat(store.replaced[FacilityType.BELL]!![0].sigungu).isEqualTo("11680")
        assertThat(store.replaced[FacilityType.SAFEHOUSE]!![0].name).isEqualTo("GS25 강남점")
        assertThat(store.replaced[FacilityType.POLICE]!![0].phone).isEqualTo("02-123-4567")
        assertThat(store.replaced[FacilityType.POLICE]!![0].name).isEqualTo("역삼지구대")
        assertThat(notifier.keys).isEmpty()
    }

    @Test
    fun `게이트 - 키 없는 원천의 종류는 시도조차 하지 않는다(결과 미포함)`() {
        enableSafemap()
        whenever(safemap.fetchPoliceFacilities()).thenReturn(
            listOf(SafemapPoliceFacility(lat = 37.5, lng = 127.0, name = "파출소")),
        )

        val result = service.refreshAll()

        assertThat(result.keys).containsExactly(FacilityType.POLICE)
        assertThat(result[FacilityType.POLICE]).isEqualTo(1)
        assertThat(store.replaced.keys).containsExactly(FacilityType.POLICE)
    }

    @Test
    fun `가로등 - safetydata 원천의 수집분을 LAMP 인덱스로 교체한다(주소·시군구 매핑)`() {
        enableStreetlight()
        whenever(streetlight.fetchStreetlights()).thenReturn(
            listOf(
                SafetydataStreetlight(lat = 37.5, lng = 127.0, address = "테헤란로 5", sigungu = "11680"),
                SafetydataStreetlight(lat = 37.51, lng = 127.02),
            ),
        )

        val result = service.refreshAll()

        assertThat(result.keys).containsExactly(FacilityType.LAMP)
        assertThat(result[FacilityType.LAMP]).isEqualTo(2)
        assertThat(store.replaced.keys).containsExactly(FacilityType.LAMP)
        val lamps: List<FacilityPoint> = store.replaced[FacilityType.LAMP]!!
        assertThat(lamps[0].type).isEqualTo(FacilityType.LAMP)
        assertThat(lamps[0].address).isEqualTo("테헤란로 5")
        assertThat(lamps[0].sigungu).isEqualTo("11680")
        assertThat(notifier.keys).isEmpty()
    }

    @Test
    fun `가로등 - 쿼터 때문에 보안등 표준데이터는 호출하지 않고 safetydata 만으로 만든다`() {
        enableStreetlight()
        enableDataGoKr()
        whenever(streetlight.fetchStreetlights()).thenReturn(
            listOf(
                SafetydataStreetlight(lat = 37.5, lng = 127.0, address = "테헤란로 5", sigungu = "11680"),
                SafetydataStreetlight(lat = 35.1018, lng = 129.032),
            ),
        )
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(null)
        whenever(dataGoKr.fetchSafehouses()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.LAMP]).isEqualTo(2)
        val lamps: List<FacilityPoint> = store.replaced[FacilityType.LAMP]!!
        assertThat(lamps.map { it.type }).containsOnly(FacilityType.LAMP)
        assertThat(lamps.map { it.sigungu }).containsExactly("11680", null)
        verify(dataGoKr, never()).fetchSecurityLamps()
    }

    @Test
    fun `가로등 - 같은 좌표가 두 번 실리면 하나로 합친다(밀도 점수 부풀림 방지)`() {
        enableStreetlight()
        whenever(streetlight.fetchStreetlights()).thenReturn(
            listOf(
                SafetydataStreetlight(lat = 37.500001, lng = 127.000001),
                SafetydataStreetlight(lat = 37.5, lng = 127.0),
            ),
        )

        val result = service.refreshAll()

        assertThat(result[FacilityType.LAMP]).isEqualTo(1)
    }

    @Test
    fun `가로등 - 원천 수집이 실패하면 교체하지 않는다(부분 교체로 커버리지가 줄지 않게)`() {
        enableStreetlight()
        whenever(streetlight.fetchStreetlights()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.LAMP]).isNull()
        assertThat(store.replaced).doesNotContainKey(FacilityType.LAMP)
        assertThat(notifier.keys).contains("facility-refresh:lamp")
    }

    @Test
    fun `실패 - 한 종류의 수집 실패(null)는 그 종류만 유지하고 다른 종류 갱신을 막지 않는다`() {
        enableDataGoKr()
        enableSafemap()
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.5, lng = 127.0)),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(null)
        whenever(safemap.fetchPoliceFacilities()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result).doesNotContainKey(FacilityType.CCTV)
        assertThat(result[FacilityType.POLICE]).isNull()
        assertThat(store.replaced.keys).containsExactly(FacilityType.BELL)
        assertThat(result[FacilityType.BELL]).isEqualTo(1)
        assertThat(notifier.keys).containsExactlyInAnyOrder(
            "facility-refresh:safehouse", "facility-refresh:police",
        )
    }

    @Test
    fun `실패 - 유효 좌표 0건이면 교체하지 않는다(데이터 소멸 방지)`() {
        enableDataGoKr()
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(DataGoKrPoint(lat = 95.0, lng = 127.0), DataGoKrPoint(lat = 37.5, lng = 200.0)),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(emptyList())

        val result = service.refreshAll()

        assertThat(result).doesNotContainKey(FacilityType.CCTV)
        assertThat(result[FacilityType.BELL]).isNull()
        assertThat(result[FacilityType.SAFEHOUSE]).isNull()
        assertThat(result).doesNotContainKey(FacilityType.LAMP)
        assertThat(store.replaced).isEmpty()
        assertThat(notifier.keys).hasSize(2)
    }

    @Test
    fun `매핑 - 좌표 범위 밖 행만 버리고 나머지는 반영한다`() {
        enableDataGoKr()
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(
                DataGoKrPoint(lat = 37.5, lng = 127.0, name = "유효"),
                DataGoKrPoint(lat = -91.0, lng = 127.0, name = "위도밖"),
            ),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(null)
        whenever(dataGoKr.fetchSecurityLamps()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.BELL]).isEqualTo(1)
        assertThat(store.replaced[FacilityType.BELL]!!.map { it.name }).containsExactly("유효")
    }

    @Test
    fun `부수효과 - refreshAll 종료 시 반환 결과를 상태 홀더에 기록한다`() {
        enableDataGoKr()
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(null)
        whenever(dataGoKr.fetchSafehouses()).thenReturn(listOf(DataGoKrPoint(lat = 37.6, lng = 127.1)))
        whenever(dataGoKr.fetchSecurityLamps()).thenReturn(null)
        val holder: FacilityRefreshStatusStore = FacilityRefreshStatusStore()
        val serviceWithHolder = FacilityRefreshService(
            dataGoKr, safemap, streetlight, store, notifier, refreshStatusStore = holder,
        )

        serviceWithHolder.refreshAll()

        assertThat(holder.findByType(FacilityType.SAFEHOUSE)?.replacedCount).isEqualTo(1)
        assertThat(holder.findByType(FacilityType.BELL)?.replacedCount).isNull()
        assertThat(holder.findByType(FacilityType.BELL)).isNotNull
        assertThat(holder.findByType(FacilityType.CCTV)).isNull()
        assertThat(holder.findByType(FacilityType.POLICE)).isNull()
    }

    @Test
    fun `게이트 - isEnabled 는 두 원천 중 하나라도 키가 있으면 true`() {
        assertThat(service.isEnabled).isFalse()

        whenever(safemap.isEnabled).thenReturn(true)
        assertThat(service.isEnabled).isTrue()

        whenever(safemap.isEnabled).thenReturn(false)
        whenever(dataGoKr.isEnabled).thenReturn(true)
        assertThat(service.isEnabled).isTrue()
    }
}
