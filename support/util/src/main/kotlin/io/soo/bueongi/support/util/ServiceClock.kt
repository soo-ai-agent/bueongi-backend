package io.soo.bueongi.support.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 서비스가 쓰는 시간대. 통계의 "오늘"·"이 시각"은 전부 이 기준이다 —
 * 사용자도 운영자도 한국에 있고, 대시보드의 하루가 UTC 자정에 바뀌면 읽는 사람이 헷갈린다.
 */
val SERVICE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/** [nowMs] 가 속한 날의 자정(밀리초). "오늘 몇 건" 을 셀 때의 시작점. */
fun todayStartMs(nowMs: Long): Long {
    val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SERVICE_ZONE).toLocalDate()
    return today.atStartOfDay(SERVICE_ZONE).toInstant().toEpochMilli()
}

/** [nowMs] 를 서비스 시간대의 시각으로 읽는다. */
fun zonedAt(nowMs: Long): ZonedDateTime =
    Instant.ofEpochMilli(nowMs).atZone(SERVICE_ZONE)
