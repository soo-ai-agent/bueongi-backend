package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/**
 * traffic 응답 전체. [totalCalls] 는 창 내 총 수신 호출 수, [windowSize] 는 집계에 쓴 기록 수(둘 다 창 상한 이하).
 * [estimatedUsers] 는 고유 익명 client-id 관측치다(실제 사용자 수 추정이 아니라 관측 하한).
 */
data class TrafficSummary(
    val totalCalls: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointTraffic>,
    val estimatedUsers: EstimatedUsers,
    /** 전체 소요시간 분위수(p50/p95/p99, ms). */
    val latency: LatencyPercentiles,
    val hourlyCalls: List<HourlyBucket>,
    /** 최근 24시간 시간대별 에러(2xx·3xx 아님) 건수 — [hourlyCalls] 와 같은 경계·같은 버킷. */
    val hourlyErrors: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
)
