package io.soo.bueongi.support.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

val SERVICE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

fun zonedAt(nowMs: Long): ZonedDateTime = Instant.ofEpochMilli(nowMs).atZone(SERVICE_ZONE)
