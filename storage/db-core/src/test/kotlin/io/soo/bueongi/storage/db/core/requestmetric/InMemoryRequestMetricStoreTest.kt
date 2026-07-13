package io.soo.bueongi.storage.db.core.requestmetric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [InMemoryRequestMetricStore] 단위 테스트 — 저장소 계약([RequestMetricStore])의 핵심 불변식을 고정한다:
 * 최근순(최신 먼저) 반환, 상한([REQUEST_METRIC_CAP]) 초과 시 오래된 것 축출, limit 절삭, 빈 조회.
 */
class InMemoryRequestMetricStoreTest {

    private val store = InMemoryRequestMetricStore()

    private fun metric(at: Long, endpoint: String = "/api/places/search", clientId: String? = "anon-1") =
        RequestMetric(
            atEpochMs = at,
            method = "GET",
            endpoint = endpoint,
            clientId = clientId,
            status = 200,
            durationMs = 10,
        )

    @Test
    fun `record 후 findRecent 는 최신이 먼저다`() {
        store.record(metric(1))
        store.record(metric(2))
        store.record(metric(3))

        val recent = store.findRecent(10)

        assertEquals(listOf(3L, 2L, 1L), recent.map { it.atEpochMs })
    }

    @Test
    fun `limit 만큼만 반환한다(최신부터)`() {
        (1L..5L).forEach { store.record(metric(it)) }

        assertEquals(listOf(5L, 4L), store.findRecent(2).map { it.atEpochMs })
    }

    @Test
    fun `clientId 가 null 이어도 그대로 저장된다`() {
        store.record(metric(1, clientId = null))

        assertEquals(listOf<String?>(null), store.findRecent(10).map { it.clientId })
    }

    @Test
    fun `상한을 초과하면 가장 오래된 것부터 축출된다`() {
        val total = REQUEST_METRIC_CAP + 50L
        (1L..total).forEach { store.record(metric(it)) }

        val all = store.findRecent(REQUEST_METRIC_CAP + 100)

        assertEquals(REQUEST_METRIC_CAP, all.size)
        assertEquals(total, all.first().atEpochMs) // 최신 유지
        assertEquals(51L, all.last().atEpochMs) // 1..50 은 축출됨
    }

    @Test
    fun `기록이 없으면 빈 목록`() {
        assertTrue(store.findRecent(10).isEmpty())
    }
}
