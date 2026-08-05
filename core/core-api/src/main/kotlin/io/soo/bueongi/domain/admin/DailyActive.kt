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

/** 일자별 활성 사용자 1건. [dateEpochMs] 는 해당 날 자정(Asia/Seoul, epoch ms). */
data class DailyActive(
    val dateEpochMs: Long,
    val distinctClients: Int,
)
