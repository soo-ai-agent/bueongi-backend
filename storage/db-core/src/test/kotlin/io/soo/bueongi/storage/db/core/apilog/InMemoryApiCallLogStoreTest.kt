package io.soo.bueongi.storage.db.core.apilog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [InMemoryApiCallLogStore] 단위 테스트 — 저장소 계약([ApiCallLogStore])의 핵심 불변식을 고정한다:
 * 최근순(최신 먼저) 반환, 상한([API_CALL_LOG_CAP]) 초과 시 오래된 것 축출, provider 필터, limit 절삭.
 */
class InMemoryApiCallLogStoreTest {

    private val store: InMemoryApiCallLogStore = InMemoryApiCallLogStore()

    private fun log(at: Long, provider: String = "KAKAO", success: Boolean = true) =
        ApiCallLog(atEpochMs = at, provider = provider, endpoint = "search", success = success, durationMs = 10)

    @Test
    fun `record 후 findRecent 는 최신이 먼저다`() {
        store.record(log(1, provider = "KAKAO"))
        store.record(log(2, provider = "TMAP"))
        store.record(log(3, provider = "DATAGOKR"))

        val recent: List<ApiCallLog> = store.findRecent(provider = null, limit = 10)

        assertEquals(listOf(3L, 2L, 1L), recent.map { it.atEpochMs })
    }

    @Test
    fun `provider 가 null 이면 전체, 지정하면 해당 원천만`() {
        store.record(log(1, provider = "KAKAO"))
        store.record(log(2, provider = "TMAP"))
        store.record(log(3, provider = "KAKAO"))

        assertEquals(3, store.findRecent(null, 10).size)
        assertEquals(listOf(3L, 1L), store.findRecent("KAKAO", 10).map { it.atEpochMs })
        assertEquals(listOf(2L), store.findRecent("TMAP", 10).map { it.atEpochMs })
    }

    @Test
    fun `limit 만큼만 반환한다(최신부터)`() {
        (1L..5L).forEach { store.record(log(it)) }

        assertEquals(listOf(5L, 4L), store.findRecent(null, 2).map { it.atEpochMs })
    }

    @Test
    fun `상한을 초과하면 가장 오래된 것부터 축출된다`() {
        val total: Long = API_CALL_LOG_CAP + 50L
        (1L..total).forEach { store.record(log(it)) }

        val all: List<ApiCallLog> = store.findRecent(null, API_CALL_LOG_CAP + 100)

        assertEquals(API_CALL_LOG_CAP, all.size)
        assertEquals(total, all.first().atEpochMs) // 최신 유지
        assertEquals(51L, all.last().atEpochMs) // 1..50 은 축출됨
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(store.findRecent(null, 10).isEmpty())
    }
}
