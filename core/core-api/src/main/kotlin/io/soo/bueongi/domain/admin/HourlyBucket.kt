package io.soo.bueongi.domain.admin

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class HourlyBucket(
    val hourStartMs: Long,
    val count: Int,
)
