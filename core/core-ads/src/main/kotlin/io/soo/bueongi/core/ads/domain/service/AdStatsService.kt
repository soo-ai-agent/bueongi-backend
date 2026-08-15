package io.soo.bueongi.core.ads.domain.service

import io.soo.bueongi.support.util.todayStartMs
import io.soo.bueongi.core.ads.domain.model.AdStatsSummary
import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.support.util.HourlyBuckets

import io.soo.bueongi.storage.db.core.adevent.AD_EVENT_CAP
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import org.springframework.stereotype.Service

@Service
class AdStatsService(
    private val adEventStore: AdEventStore,
) {

    fun record(placement: String) {
        adEventStore.record(AdEvent(atEpochMs = System.currentTimeMillis(), placement = placement))
    }

    fun summarize(): AdStatsSummary {
        val events: List<AdEvent> = adEventStore.findRecent(AD_EVENT_CAP)
        return AdStatsSummary(
            total = events.size,
            today = todayCount(events),
            byPlacement = byPlacement(events),
            hourlyTrend = hourlyTrend(events),
        )
    }

    private fun todayCount(events: List<AdEvent>): Int {
        val todayStartMs: Long = todayStartMs(System.currentTimeMillis())
        var count: Int = 0
        for (event in events) {
            if (event.atEpochMs >= todayStartMs) {
                count++
            }
        }
        return count
    }

    private fun byPlacement(events: List<AdEvent>): Map<String, Int> {
        val counts: MutableMap<String, Int> = HashMap<String, Int>()
        for (event in events) {
            counts[event.placement] = (counts[event.placement] ?: 0) + 1
        }
        val rows: LinkedHashMap<String, Int> = LinkedHashMap<String, Int>()
        for (placement in TRACKED_PLACEMENTS) {
            rows[placement] = counts[placement] ?: 0
        }
        return rows
    }

    private fun hourlyTrend(events: List<AdEvent>): List<HourlyBucket> {
        val timestamps: ArrayList<Long> = ArrayList<Long>()
        for (event in events) {
            timestamps.add(event.atEpochMs)
        }
        return HourlyBuckets.countOf(timestamps)
    }

    companion object {
        val TRACKED_PLACEMENTS: List<String> = listOf("interstitial", "native")
    }
}
