package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.ApiProvider
import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_CAP
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** api-status 응답 전체. [windowSize] 는 집계에 쓴 로그 총수, [hourlyTrend] 는 최근 24시간 전체 호출량 추이. */
data class ApiStatusSummary(
    val providers: List<ProviderStatus>,
    val windowSize: Int,
    val hourlyTrend: List<HourlyBucket>,
)
