package io.soo.bueongi.support.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

val SERVICE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

fun todayStartMs(nowMs: Long): Long {
    val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SERVICE_ZONE).toLocalDate()
    return today.atStartOfDay(SERVICE_ZONE).toInstant().toEpochMilli()
}

fun zonedAt(nowMs: Long): ZonedDateTime = Instant.ofEpochMilli(nowMs).atZone(SERVICE_ZONE)
