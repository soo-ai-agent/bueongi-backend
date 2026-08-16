package io.soo.bueongi.storage.db.core.safetyspot

import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotRefreshRecord
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.core.enums.SafetySpotType
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
    fun `기록한 결과를 종류별로 되돌려주고 미포함 종류는 null 이다`() {
        val store = SafetySpotRefreshStatusStore()

        store.record(
            mapOf(
                SafetySpotType.CCTV to 372,
                SafetySpotType.POLICE to null,
            ),
            atEpochMs = 1_700_000_000_000L,
        )

        assertThat(store.findByType(SafetySpotType.CCTV))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 1_700_000_000_000L, replacedCount = 372))
        assertThat(store.findByType(SafetySpotType.POLICE))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 1_700_000_000_000L, replacedCount = null))
        assertThat(store.findByType(SafetySpotType.LAMP)).isNull()
    }

    @Test
    fun `다시 기록하면 같은 종류는 갱신되고 이번에 없던 종류는 남는다`() {
        val store = SafetySpotRefreshStatusStore()

        store.record(mapOf(SafetySpotType.CCTV to 100), atEpochMs = 1L)
        store.record(mapOf(SafetySpotType.CCTV to 120, SafetySpotType.BELL to 5), atEpochMs = 2L)

        assertThat(store.findByType(SafetySpotType.CCTV))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 2L, replacedCount = 120))
        assertThat(store.findByType(SafetySpotType.BELL))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 2L, replacedCount = 5))
    }

    @Test
    fun `업로드 기록은 이번 갱신 대상이 아니면 이후 정기 갱신에도 남는다`() {
        val store = SafetySpotRefreshStatusStore()

        store.recordUpload(SafetySpotType.CCTV, replacedCount = 315_459, atEpochMs = 1L)
        store.record(mapOf(SafetySpotType.BELL to 88_621), atEpochMs = 2L)

        assertThat(store.findByType(SafetySpotType.CCTV))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 1L, replacedCount = 315_459))
        assertThat(store.findByType(SafetySpotType.BELL))
            .isEqualTo(SafetySpotRefreshRecord(atEpochMs = 2L, replacedCount = 88_621))
    }

    @Test
    fun `갱신과 업로드가 동시에 기록해도 어느 쪽도 유실되지 않는다`() {
        val store = SafetySpotRefreshStatusStore()
        val rounds: Int = 200
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool: ExecutorService = Executors.newFixedThreadPool(2)

        pool.execute {
            start.await()
            repeat(rounds) { store.record(mapOf(SafetySpotType.BELL to it), atEpochMs = it.toLong()) }
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
