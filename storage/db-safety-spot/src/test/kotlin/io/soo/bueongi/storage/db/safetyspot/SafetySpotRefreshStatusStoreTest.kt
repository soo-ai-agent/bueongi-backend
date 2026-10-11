package io.soo.bueongi.storage.db.safetyspot

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SafetySpotRefreshStatusStoreTest {
    @Test
    fun `기록 전에는 어떤 종류도 null 이다`() {
        val store = SafetySpotRefreshStatusStore()

        assertThat(store.findByType(SafetySpotType.CCTV)).isNull()
    }

    @Test
    fun `같은 종류를 다시 올리면 갱신되고 다른 종류의 기록은 남는다`() {
        val store = SafetySpotRefreshStatusStore()

        store.recordUpload(SafetySpotType.CCTV, replacedCount = 100, atEpochMs = 1L)
        store.recordUpload(SafetySpotType.BELL, replacedCount = 5, atEpochMs = 2L)
        store.recordUpload(SafetySpotType.CCTV, replacedCount = 120, atEpochMs = 3L)

        assertThat(store.findByType(SafetySpotType.CCTV))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 3L, replacedCount = 120))
        assertThat(store.findByType(SafetySpotType.BELL))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 2L, replacedCount = 5))
    }

    @Test
    fun `두 종류를 동시에 올려도 어느 쪽 기록도 유실되지 않는다`() {
        val store = SafetySpotRefreshStatusStore()
        val rounds: Int = 200
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool: ExecutorService = Executors.newFixedThreadPool(2)

        pool.execute {
            start.await()
            repeat(rounds) { store.recordUpload(SafetySpotType.BELL, replacedCount = it, atEpochMs = it.toLong()) }
            done.countDown()
        }
        pool.execute {
            start.await()
            repeat(rounds) { store.recordUpload(SafetySpotType.CCTV, replacedCount = it, atEpochMs = it.toLong()) }
            done.countDown()
        }
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        pool.shutdown()

        assertThat(store.findByType(SafetySpotType.BELL)).isNotNull
        assertThat(store.findByType(SafetySpotType.CCTV)).isNotNull
    }
}
