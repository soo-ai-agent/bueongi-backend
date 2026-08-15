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

data class EstimatedUsers(
    val today: Int,
    val last7d: Int,
    val last30d: Int,
    val callsWithoutClientId: Int,
    val newToday: Int,
    val returningToday: Int,
)
