package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [AdStatsService] 집계 정확성 — 저장된 광고 노출 기록만으로 총계·오늘 노출 수·지면별 카운트(2종 고정 키)·
 * 최근 24시간 시간대 트렌드를 계산한다. 저장소는 넘겨준 기록을 그대로 돌려주는 페이크(가장 얇은 대역)로 대체한다.
 */
class AdStatsServiceTest {

    private fun store(events: List<AdEvent>): AdEventStore = object : AdEventStore {
        override fun record(event: AdEvent) = Unit
        override fun findRecent(limit: Int): List<AdEvent> = events.take(limit)
    }

    private fun event(at: Long = System.currentTimeMillis(), placement: String = "interstitial") =
        AdEvent(atEpochMs = at, placement = placement)

    @Test
    fun `record 는 현재시각으로 AdEvent 를 만들어 저장소에 넣는다`() {
        val recorded = mutableListOf<AdEvent>()
        val capturing = object : AdEventStore {
            override fun record(event: AdEvent) {
                recorded += event
            }

            override fun findRecent(limit: Int): List<AdEvent> = recorded
        }
        val before = System.currentTimeMillis()

        AdStatsService(capturing).record("native")

        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().placement).isEqualTo("native")
        assertThat(recorded.single().atEpochMs).isGreaterThanOrEqualTo(before)
    }

    @Test
    fun `total 은 창 내 총 노출 수 today 는 Asia_Seoul 자정 이후 노출 수다`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            event(at = now),
            event(at = now),
            event(at = now - 40 * DAY_MS), // 창엔 있으나 오늘은 아님
        )

        val summary = AdStatsService(store(events)).summarize()

        assertThat(summary.total).isEqualTo(3)
        assertThat(summary.today).isEqualTo(2)
    }

    @Test
    fun `byPlacement 는 지면별 노출 수를 집계한다`() {
        val events = listOf(
            event(placement = "interstitial"),
            event(placement = "interstitial"),
            event(placement = "native"),
        )

        val byPlacement = AdStatsService(store(events)).summarize().byPlacement

        assertThat(byPlacement).containsOnlyKeys("interstitial", "native")
        assertThat(byPlacement["interstitial"]).isEqualTo(2)
        assertThat(byPlacement["native"]).isEqualTo(1)
    }

    @Test
    fun `노출이 없는 지면도 byPlacement 키로 0 이 나온다`() {
        val events = listOf(event(placement = "interstitial"))

        val byPlacement = AdStatsService(store(events)).summarize().byPlacement

        assertThat(byPlacement).containsOnlyKeys("interstitial", "native")
        assertThat(byPlacement["native"]).isEqualTo(0)
    }

    @Test
    fun `hourlyTrend 는 24버킷 오래된→최신이며 창 밖 노출은 제외한다`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            event(at = now),
            event(at = now - 25 * HOUR_MS), // 24시간 창 밖
        )

        val trend = AdStatsService(store(events)).summarize().hourlyTrend

        assertThat(trend).hasSize(24)
        assertThat(trend.map { it.hourStartMs }).isSorted()
        assertThat(trend.sumOf { it.count }).isEqualTo(1) // 창 안 1건만
    }

    @Test
    fun `빈 기록이면 total today 0 지면 2키 0 시간대 24개 0`() {
        val summary = AdStatsService(store(emptyList())).summarize()

        assertThat(summary.total).isEqualTo(0)
        assertThat(summary.today).isEqualTo(0)
        assertThat(summary.byPlacement).containsOnlyKeys("interstitial", "native")
        assertThat(summary.byPlacement.values).allMatch { it == 0 }
        assertThat(summary.hourlyTrend).hasSize(24)
        assertThat(summary.hourlyTrend.sumOf { it.count }).isEqualTo(0)
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }
}
