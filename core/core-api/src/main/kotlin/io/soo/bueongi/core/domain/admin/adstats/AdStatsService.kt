package io.soo.bueongi.core.domain.admin.adstats

import io.soo.bueongi.support.util.HourlyBucket
import io.soo.bueongi.support.util.HourlyBuckets

import io.soo.bueongi.storage.db.core.adevent.AD_EVENT_CAP
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        return events.count { it.atEpochMs >= todayStartMs }
    }

    private fun byPlacement(events: List<AdEvent>): Map<String, Int> {
        val counts: Map<String, Int> = events.groupingBy { it.placement }.eachCount()
        return TRACKED_PLACEMENTS.associateWith { placement -> counts[placement] ?: 0 }
    }

    private fun hourlyTrend(events: List<AdEvent>): List<HourlyBucket> =
        HourlyBuckets.countOf(events.map { it.atEpochMs })

    private fun todayStartMs(nowMs: Long): Long {
        val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    companion object {
        val TRACKED_PLACEMENTS: List<String> = listOf("interstitial", "native")

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
