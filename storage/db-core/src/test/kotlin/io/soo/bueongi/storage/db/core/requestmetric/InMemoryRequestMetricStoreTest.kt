package io.soo.bueongi.storage.db.core.requestmetric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryRequestMetricStoreTest {
    private fun metric(at: Long, clientId: String? = "anon-1") =
        RequestMetric(
            atEpochMs = at,
            method = "GET",
            endpoint = "/api/places/search",
            clientId = clientId,
            status = 200,
            durationMs = 10,
        )

    @Test
    fun `이 저장소의 상한은 REQUEST_METRIC_CAP 이다`() {
        val store = InMemoryRequestMetricStore()
        val total: Long = REQUEST_METRIC_CAP + 50L
        (1L..total).forEach { store.record(metric(it)) }

        val all: List<RequestMetric> = store.findRecent(REQUEST_METRIC_CAP + 100)

        assertEquals(REQUEST_METRIC_CAP, all.size)
        assertEquals(total, all.first().atEpochMs)
    }

    @Test
    fun `헤더를 안 보낸 호출자의 clientId 는 null 로 남는다`() {
        val store = InMemoryRequestMetricStore()
        store.record(metric(1, clientId = null))

        assertEquals(listOf<String?>(null), store.findRecent(10).map { it.clientId })
    }
}
