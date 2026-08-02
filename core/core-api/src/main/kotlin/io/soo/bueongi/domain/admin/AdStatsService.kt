package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.adevent.AD_EVENT_CAP
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

/**
 * 관리자 페이지용 "광고 노출" 집계 — 이미 저장된 광고 노출 기록([AdEventStore])만 읽어 총 노출 수·
 * 오늘 노출 수·지면별 카운트·최근 24시간 시간대 트렌드를 계산한다(새 저장소 없음).
 *
 * [RouteStatsService]·[RequestMetricsService] 와 같은 관례 — 창(window)은 최근 [AD_EVENT_CAP] 건이고
 * 시각 처리는 Asia/Seoul 기준, 시간대 추이는 24버킷 [HourlyBucket] 을 **같은 경계로 재사용**한다.
 * 지면 2종([TRACKED_PLACEMENTS])은 노출이 0건이어도 항상 키로 나온다.
 */
@Service
class AdStatsService(
    private val adEventStore: AdEventStore,
) {

    /** 광고 노출 1건 기록 — 지면 허용값 검증은 요청 DTO 계층이 끝냈다고 가정한다(유효 값만 들어온다). */
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

    /** Asia/Seoul 자정 이후 노출 수. */
    private fun todayCount(events: List<AdEvent>): Int {
        val todayStartMs: Long = todayStartMs(System.currentTimeMillis())
        return events.count { it.atEpochMs >= todayStartMs }
    }

    /** 지면 2종([TRACKED_PLACEMENTS])을 항상 키로 노출한다(노출이 0건인 지면도 0 으로 나온다). */
    private fun byPlacement(events: List<AdEvent>): Map<String, Int> {
        val counts: Map<String, Int> = events.groupingBy { it.placement }.eachCount()
        return TRACKED_PLACEMENTS.associateWith { placement -> counts[placement] ?: 0 }
    }

    /**
     * 최근 24시간 시간대별 광고 노출 수 — 버킷 경계는 [HourlyBuckets] 가 단일 정의한다(노출 0건인 시간도 노출).
     * 다른 관리자 지표와 같은 경계·같은 [HourlyBucket] 이라 프론트 막대 렌더를 공유한다.
     */
    private fun hourlyTrend(events: List<AdEvent>): List<HourlyBucket> =
        HourlyBuckets.countOf(events.map { it.atEpochMs })

    private fun todayStartMs(nowMs: Long): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    companion object {
        /** 항상 노출하는 광고 지면 2종. 노출이 없어도 count=0 으로 나온다(요청 DTO 의 허용값과 같은 집합). */
        val TRACKED_PLACEMENTS: List<String> = listOf("interstitial", "native")

        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}

/**
 * ad-stats 응답 전체. [total] 은 창 내 총 광고 노출 수, [today] 는 Asia/Seoul 자정 이후 노출 수,
 * [byPlacement] 는 지면별 카운트(2종 고정 키, 0 이어도 포함), [hourlyTrend] 는 최근 24시간 시간대 트렌드
 * ([RouteStatsService]·[RequestMetricsService] 와 같은 경계·같은 [HourlyBucket]).
 */
data class AdStatsSummary(
    val total: Int,
    val today: Int,
    val byPlacement: Map<String, Int>,
    val hourlyTrend: List<HourlyBucket>,
)
