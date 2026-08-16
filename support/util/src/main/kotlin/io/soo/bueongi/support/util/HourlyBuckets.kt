package io.soo.bueongi.support.util

import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object HourlyBuckets {

    fun countOf(timestampsMs: List<Long>): List<HourlyBucket> {
        val currentHour: ZonedDateTime = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(SERVICE_ZONE)
            .truncatedTo(ChronoUnit.HOURS)
        val buckets: ArrayList<HourlyBucket> = ArrayList()
        for (hoursAgo in BUCKET_COUNT - 1 downTo 0) {
            val bucketStart: ZonedDateTime = currentHour.minusHours(hoursAgo.toLong())
            val startMs: Long = bucketStart.toInstant().toEpochMilli()
            val endMs: Long = bucketStart.plusHours(1).toInstant().toEpochMilli()
            var count: Int = 0
            for (timestampMs in timestampsMs) {
                if (timestampMs >= startMs && timestampMs < endMs) {
                    count++
                }
            }
            buckets.add(HourlyBucket(hourStartMs = startMs, count = count))
        }
        return buckets
    }

    const val BUCKET_COUNT: Int = 24
}
