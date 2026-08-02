package io.soo.bueongi.domain.admin

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 최근 24시간 시간대 버킷을 만드는 순수 함수 — 관리자 지표의 시간축을
 * [ApiStatusService](외부 API 호출)·[RouteStatsService](추천 요청)·[AdStatsService](광고 노출)·
 * [RequestMetricsService](수신 트래픽·에러)가 **같은 경계**로 계산하도록 단일 출처로 둔다([Percentiles] 와 같은 이유).
 *
 * 경계 정의가 서비스마다 갈라지면 같은 화면의 막대들이 서로 다른 시간축 위에 그려지므로,
 * 버킷 골격은 여기 한 곳에만 존재한다. 각 서비스는 "무엇을 셀지"만 정한다.
 */
object HourlyBuckets {

    /**
     * [timestampsMs] 를 최근 24시간 1시간 버킷으로 센다(오래된→최신, Asia/Seoul 정각 경계).
     *
     * 현재 시각을 Seoul 기준 정각으로 내림한 버킷을 마지막으로, 그 앞 23개를 채운다 —
     * 0건인 시간도 count=0 으로 노출해 프론트가 빈 구간을 그대로 그린다.
     * 버킷 구간은 시작 포함·끝 제외(`start <= t < end`)라 경계 시각이 두 버킷에 겹쳐 세어지지 않는다.
     */
    fun countOf(timestampsMs: List<Long>): List<HourlyBucket> {
        val currentHour: ZonedDateTime = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(SEOUL)
            .truncatedTo(ChronoUnit.HOURS)
        return (BUCKET_COUNT - 1 downTo 0).map { hoursAgo ->
            val bucketStart: ZonedDateTime = currentHour.minusHours(hoursAgo.toLong())
            val startMs: Long = bucketStart.toInstant().toEpochMilli()
            val endMs: Long = bucketStart.plusHours(1).toInstant().toEpochMilli()
            val count: Int = timestampsMs.count { it >= startMs && it < endMs }
            HourlyBucket(hourStartMs = startMs, count = count)
        }
    }

    /** 시간대 트렌드 버킷 수(최근 24시간, 1시간 단위) — 모든 관리자 지표가 이 개수를 공유한다. */
    const val BUCKET_COUNT: Int = 24

    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
}

/**
 * 시간대 트렌드 버킷 1건. [hourStartMs] 는 해당 1시간 버킷의 시작(epoch ms, Asia/Seoul 정각).
 * 관리자 응답 4종(api-status·route-stats·ad-stats·traffic)이 같은 shape 로 공유한다.
 */
data class HourlyBucket(
    val hourStartMs: Long,
    val count: Int,
)
