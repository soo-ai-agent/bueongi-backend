package io.soo.bueongi.core.domain.admin.traffic

import io.soo.bueongi.core.domain.admin.HourlyBucket
import io.soo.bueongi.core.domain.admin.apistatus.LatencyPercentiles

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

data class TrafficSummary(
    val totalCalls: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointTraffic>,
    val estimatedUsers: EstimatedUsers,
    val latency: LatencyPercentiles,
    val hourlyCalls: List<HourlyBucket>,
    val hourlyErrors: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
)
