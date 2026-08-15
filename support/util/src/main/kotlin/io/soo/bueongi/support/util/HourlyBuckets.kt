package io.soo.bueongi.support.util

import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object HourlyBuckets {

    fun countOf(timestampsMs: List<Long>): List<HourlyBucket> {
        val currentHour: ZonedDateTime = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(SERVICE_ZONE)
            .truncatedTo(ChronoUnit.HOURS)
        return (BUCKET_COUNT - 1 downTo 0).map { hoursAgo ->
            val bucketStart: ZonedDateTime = currentHour.minusHours(hoursAgo.toLong())
            val startMs: Long = bucketStart.toInstant().toEpochMilli()
            val endMs: Long = bucketStart.plusHours(1).toInstant().toEpochMilli()
            val count: Int = timestampsMs.count { it >= startMs && it < endMs }
            HourlyBucket(hourStartMs = startMs, count = count)
        }
    }

    const val BUCKET_COUNT: Int = 24
}
