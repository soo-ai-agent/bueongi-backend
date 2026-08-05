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

/** 소요시간 백분위수(ms) 묶음 — nearest-rank(보간 없음). 빈 창이면 전부 0. */
data class LatencyPercentiles(
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
)
