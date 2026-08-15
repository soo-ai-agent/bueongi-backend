package io.soo.bueongi.core.domain.admin

import io.soo.bueongi.storage.db.core.adevent.AD_EVENT_CAP
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AdStatsSummary(
    val total: Int,
    val today: Int,
    val byPlacement: Map<String, Int>,
    val hourlyTrend: List<HourlyBucket>,
)
