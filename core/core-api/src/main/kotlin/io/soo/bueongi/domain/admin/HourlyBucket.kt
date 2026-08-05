package io.soo.bueongi.domain.admin

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 시간대 트렌드 버킷 1건. [hourStartMs] 는 해당 1시간 버킷의 시작(epoch ms, Asia/Seoul 정각).
 * 관리자 응답 4종(api-status·route-stats·ad-stats·traffic)이 같은 shape 로 공유한다.
 */
data class HourlyBucket(
    val hourStartMs: Long,
    val count: Int,
)
