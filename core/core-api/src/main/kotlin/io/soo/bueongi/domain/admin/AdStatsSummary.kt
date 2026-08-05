package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.adevent.AD_EVENT_CAP
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
