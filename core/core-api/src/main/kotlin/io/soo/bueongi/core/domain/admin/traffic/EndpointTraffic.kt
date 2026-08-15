package io.soo.bueongi.core.domain.admin.traffic

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

data class EndpointTraffic(
    val method: String,
    val endpoint: String,
    val count: Int,
    val successRate: Double,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val distinctClients: Int,
)
