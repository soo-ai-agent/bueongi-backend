package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.datagokr.DataGoKrFacilityClient
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.client.safemap.SafemapPoliceClient
import io.soo.bueongi.client.safemap.SafemapPoliceFacility
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [FacilityRefreshService] 오케스트레이션 테스트 — 클라이언트(mock)와 가짜 싱크로
 * "성공 시에만 교체, 실패/0건은 기존 유지, 종류·원천별 독립 게이트" 안전 규칙을 고정한다.
 */
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
        override val isEnabled = true
        override fun notify(key: String, title: String, detail: String) {
            keys.add(key)
        }
    }

    private val dataGoKr: DataGoKrFacilityClient = mock()
    private val safemap: SafemapPoliceClient = mock()
    private val store = FakeStore()
    private val notifier = RecordingNotifier()
    private val service = FacilityRefreshService(dataGoKr, safemap, store, notifier)

    private fun enableDataGoKr() = whenever(dataGoKr.isEnabled).thenReturn(true)
    private fun enableSafemap() = whenever(safemap.isEnabled).thenReturn(true)

    @Test
    fun `정상 - 두 원천의 수집분을 도메인 모델로 매핑해 종류별 인덱스를 교체한다`() {
        enableDataGoKr()
        enableSafemap()
        whenever(dataGoKr.fetchCctv()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.5, lng = 127.0, name = "기관", address = "주소")),
        )
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(
                DataGoKrPoint(lat = 37.51, lng = 127.02, name = "강남구청", sigungu = "11680"),
                DataGoKrPoint(lat = 37.52, lng = 127.03, name = "서초구청"),
            ),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.498, lng = 127.027, name = "GS25 강남점", address = "강남대로 396")),
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

        assertThat(result[FacilityType.CCTV]).isEqualTo(1)
        assertThat(result[FacilityType.BELL]).isEqualTo(2)
        assertThat(result[FacilityType.SAFEHOUSE]).isEqualTo(1)
        assertThat(result[FacilityType.POLICE]).isEqualTo(1)
        assertThat(store.replaced[FacilityType.CCTV]!![0].type).isEqualTo(FacilityType.CCTV)
        assertThat(store.replaced[FacilityType.BELL]!![0].sigungu).isEqualTo("11680")
        assertThat(store.replaced[FacilityType.SAFEHOUSE]!![0].name).isEqualTo("GS25 강남점")
        // 치안시설은 전화번호까지 저장돼야 한다(긴급 시트 파출소 연결).
        assertThat(store.replaced[FacilityType.POLICE]!![0].phone).isEqualTo("02-123-4567")
        assertThat(store.replaced[FacilityType.POLICE]!![0].name).isEqualTo("역삼지구대")
        assertThat(notifier.keys).isEmpty() // 전부 성공 → 운영 알림 없음
    }

    @Test
    fun `게이트 - 키 없는 원천의 종류는 시도조차 하지 않는다(결과 미포함)`() {
        enableSafemap() // data.go.kr 는 미설정
        whenever(safemap.fetchPoliceFacilities()).thenReturn(
            listOf(SafemapPoliceFacility(lat = 37.5, lng = 127.0, name = "파출소")),
        )

        val result = service.refreshAll()

        assertThat(result.keys).containsExactly(FacilityType.POLICE)
        assertThat(result[FacilityType.POLICE]).isEqualTo(1)
        assertThat(store.replaced.keys).containsExactly(FacilityType.POLICE)
    }

    @Test
    fun `실패 - 한 종류의 수집 실패(null)는 그 종류만 유지하고 다른 종류 갱신을 막지 않는다`() {
        enableDataGoKr()
        enableSafemap()
        whenever(dataGoKr.fetchCctv()).thenReturn(null)
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.5, lng = 127.0)),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(null)
        whenever(safemap.fetchPoliceFacilities()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isNull()
        assertThat(result[FacilityType.POLICE]).isNull()
        assertThat(store.replaced.keys).containsExactly(FacilityType.BELL)
        assertThat(result[FacilityType.BELL]).isEqualTo(1)
        // 실패한 종류(cctv·safehouse·police)만 알림 — 성공한 bell 은 미포함.
        assertThat(notifier.keys).containsExactlyInAnyOrder(
            "facility-refresh:cctv", "facility-refresh:safehouse", "facility-refresh:police",
        )
    }

    @Test
    fun `실패 - 유효 좌표 0건이면 교체하지 않는다(데이터 소멸 방지)`() {
        enableDataGoKr()
        whenever(dataGoKr.fetchCctv()).thenReturn(emptyList())
        // 전부 좌표 범위 밖 → 매핑 후 0건.
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(
            listOf(DataGoKrPoint(lat = 95.0, lng = 127.0), DataGoKrPoint(lat = 37.5, lng = 200.0)),
        )
        whenever(dataGoKr.fetchSafehouses()).thenReturn(emptyList())

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isNull()
        assertThat(result[FacilityType.BELL]).isNull()
        assertThat(result[FacilityType.SAFEHOUSE]).isNull()
        assertThat(store.replaced).isEmpty()
        assertThat(notifier.keys).hasSize(3) // 0건 이상 신호도 종류별 알림
    }

    @Test
    fun `매핑 - 좌표 범위 밖 행만 버리고 나머지는 반영한다`() {
        enableDataGoKr()
        whenever(dataGoKr.fetchCctv()).thenReturn(
            listOf(
                DataGoKrPoint(lat = 37.5, lng = 127.0, name = "유효"),
                DataGoKrPoint(lat = -91.0, lng = 127.0, name = "위도밖"),
            ),
        )
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(null)
        whenever(dataGoKr.fetchSafehouses()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isEqualTo(1)
        assertThat(store.replaced[FacilityType.CCTV]!!.map { it.name }).containsExactly("유효")
    }

    @Test
    fun `부수효과 - refreshAll 종료 시 반환 결과를 상태 홀더에 기록한다`() {
        enableDataGoKr()
        whenever(dataGoKr.fetchCctv()).thenReturn(listOf(DataGoKrPoint(lat = 37.5, lng = 127.0)))
        whenever(dataGoKr.fetchEmergencyBells()).thenReturn(null) // 수집 실패 → kept(count=null)
        whenever(dataGoKr.fetchSafehouses()).thenReturn(listOf(DataGoKrPoint(lat = 37.6, lng = 127.1)))
        val holder: FacilityRefreshStatusStore = FacilityRefreshStatusStore()
        val serviceWithHolder = FacilityRefreshService(
            dataGoKr, safemap, store, notifier, refreshStatusStore = holder,
        )

        serviceWithHolder.refreshAll()

        assertThat(holder.findByType(FacilityType.CCTV)?.replacedCount).isEqualTo(1) // replaced
        assertThat(holder.findByType(FacilityType.BELL)?.replacedCount).isNull() // kept
        assertThat(holder.findByType(FacilityType.BELL)).isNotNull // 기록은 됨(never 아님)
        assertThat(holder.findByType(FacilityType.POLICE)).isNull() // 키 없는 원천 → never
    }

    @Test
    fun `게이트 - isEnabled 는 두 원천 중 하나라도 키가 있으면 true`() {
        assertThat(service.isEnabled).isFalse() // 둘 다 미설정

        whenever(safemap.isEnabled).thenReturn(true)
        assertThat(service.isEnabled).isTrue()

        whenever(safemap.isEnabled).thenReturn(false)
        whenever(dataGoKr.isEnabled).thenReturn(true)
        assertThat(service.isEnabled).isTrue()
    }
}
