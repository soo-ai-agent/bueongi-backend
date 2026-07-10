package io.soo.bueongi.domain.facility

import io.soo.bueongi.client.datagokr.DataGoKrFacilityClient
import io.soo.bueongi.client.datagokr.DataGoKrPoint
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityLiveStore
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [FacilityRefreshService] 오케스트레이션 테스트 — 클라이언트(mock)와 가짜 싱크로
 * "성공 시에만 교체, 실패/0건은 기존 유지, 종류별 독립" 안전 규칙을 고정한다.
 */
class FacilityRefreshServiceTest {

    private class FakeStore : FacilityLiveStore {
        val replaced = LinkedHashMap<FacilityType, List<FacilityPoint>>()
        override fun replace(type: FacilityType, points: List<FacilityPoint>): Int {
            replaced[type] = points
            return points.size
        }
    }

    private val client: DataGoKrFacilityClient = mock()
    private val store = FakeStore()
    private val service = FacilityRefreshService(client, store)

    @Test
    fun `정상 - 수집분을 도메인 모델로 매핑해 종류별 인덱스를 교체한다`() {
        whenever(client.fetchCctv()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.5, lng = 127.0, name = "기관", address = "주소")),
        )
        whenever(client.fetchEmergencyBells()).thenReturn(
            listOf(
                DataGoKrPoint(lat = 37.51, lng = 127.02, name = "강남구청", sigungu = "11680"),
                DataGoKrPoint(lat = 37.52, lng = 127.03, name = "서초구청"),
            ),
        )
        whenever(client.fetchSafehouses()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.498, lng = 127.027, name = "GS25 강남점", address = "강남대로 396")),
        )

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isEqualTo(1)
        assertThat(result[FacilityType.BELL]).isEqualTo(2)
        assertThat(result[FacilityType.SAFEHOUSE]).isEqualTo(1)
        assertThat(store.replaced[FacilityType.SAFEHOUSE]!![0].name).isEqualTo("GS25 강남점")
        assertThat(store.replaced[FacilityType.CCTV]!![0].name).isEqualTo("기관")
        assertThat(store.replaced[FacilityType.CCTV]!![0].type).isEqualTo(FacilityType.CCTV)
        assertThat(store.replaced[FacilityType.BELL]!![0].sigungu).isEqualTo("11680")
    }

    @Test
    fun `실패 - 한 종류의 수집 실패(null)는 그 종류만 유지하고 다른 종류 갱신을 막지 않는다`() {
        whenever(client.fetchCctv()).thenReturn(null)
        whenever(client.fetchEmergencyBells()).thenReturn(
            listOf(DataGoKrPoint(lat = 37.5, lng = 127.0)),
        )
        whenever(client.fetchSafehouses()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isNull()
        assertThat(store.replaced).doesNotContainKey(FacilityType.CCTV)
        assertThat(result[FacilityType.BELL]).isEqualTo(1)
    }

    @Test
    fun `실패 - 유효 좌표 0건이면 교체하지 않는다(데이터 소멸 방지)`() {
        whenever(client.fetchCctv()).thenReturn(emptyList())
        // 전부 좌표 범위 밖 → 매핑 후 0건.
        whenever(client.fetchEmergencyBells()).thenReturn(
            listOf(DataGoKrPoint(lat = 95.0, lng = 127.0), DataGoKrPoint(lat = 37.5, lng = 200.0)),
        )
        whenever(client.fetchSafehouses()).thenReturn(emptyList())

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isNull()
        assertThat(result[FacilityType.BELL]).isNull()
        assertThat(result[FacilityType.SAFEHOUSE]).isNull()
        assertThat(store.replaced).isEmpty()
    }

    @Test
    fun `매핑 - 좌표 범위 밖 행만 버리고 나머지는 반영한다`() {
        whenever(client.fetchCctv()).thenReturn(
            listOf(
                DataGoKrPoint(lat = 37.5, lng = 127.0, name = "유효"),
                DataGoKrPoint(lat = -91.0, lng = 127.0, name = "위도밖"),
            ),
        )
        whenever(client.fetchEmergencyBells()).thenReturn(null)
        whenever(client.fetchSafehouses()).thenReturn(null)

        val result = service.refreshAll()

        assertThat(result[FacilityType.CCTV]).isEqualTo(1)
        assertThat(store.replaced[FacilityType.CCTV]!!.map { it.name }).containsExactly("유효")
    }

    @Test
    fun `게이트 - isEnabled 는 클라이언트 키 설정 여부를 그대로 위임한다`() {
        whenever(client.isEnabled).thenReturn(false)
        assertThat(service.isEnabled).isFalse()

        whenever(client.isEnabled).thenReturn(true)
        assertThat(service.isEnabled).isTrue()
    }
}
