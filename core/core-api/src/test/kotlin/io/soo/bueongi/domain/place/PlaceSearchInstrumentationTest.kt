package io.soo.bueongi.domain.place

import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [PlaceSearchService] 의 외부 API 접속 내역 계측 검증 — 실연동 성공/실패가 각각 [ApiCallLogStore] 에 기록되고,
 * 기존 동작(성공 시 실결과 반환, 실패 시 시드 폴백)은 불변임을 고정한다.
 */
class PlaceSearchInstrumentationTest {

    private class RecordingStore : ApiCallLogStore {
        val logs: ArrayList<ApiCallLog> = ArrayList<ApiCallLog>()
        override fun record(log: ApiCallLog) {
            logs.add(log)
        }
        override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> = logs
    }

    private val store = RecordingStore()

    @Test
    fun `실연동 성공은 KAKAO search 성공으로 기록되고 실결과를 반환한다`() {
        val remote = object : RemotePlaceProvider {
            override val isEnabled = true
            override fun search(keyword: String): List<PlaceItem> =
                listOf(PlaceItem(name = "리모트결과", address = "서울", lat = 37.5, lng = 127.0))
        }
        val service = PlaceSearchService(SeedPlaceProvider(), remote, apiCallLogStore = store)

        val result = service.search("강남")

        assertThat(result.map { it.name }).containsExactly("리모트결과") // 시드 아닌 실결과
        assertThat(store.logs).hasSize(1)
        assertThat(store.logs[0].provider).isEqualTo("KAKAO")
        assertThat(store.logs[0].endpoint).isEqualTo("search")
        assertThat(store.logs[0].success).isTrue()
    }

    @Test
    fun `실연동 실패는 KAKAO search 실패로 기록되고 시드로 폴백한다`() {
        val remote = object : RemotePlaceProvider {
            override val isEnabled = true
            override fun search(keyword: String): List<PlaceItem> =
                throw RuntimeException("upstream 500")
        }
        val service = PlaceSearchService(SeedPlaceProvider(), remote, apiCallLogStore = store)

        val result = service.search("강남")

        assertThat(result).isNotEmpty // 시드 폴백 결과
        assertThat(store.logs).hasSize(1)
        assertThat(store.logs[0].provider).isEqualTo("KAKAO")
        assertThat(store.logs[0].success).isFalse()
        assertThat(store.logs[0].detail).contains("upstream 500")
    }
}
