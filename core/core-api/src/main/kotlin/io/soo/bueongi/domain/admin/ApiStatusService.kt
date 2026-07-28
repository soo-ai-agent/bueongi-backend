package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_CAP
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 관리자 페이지용 "외부 API 상태 요약·쿼터" 집계 — 이미 저장된 접속 내역([ApiCallLogStore])만 읽어
 * 원천(provider)별로 성공률·평균/95분위 소요시간·최근 성공/실패 시각·오늘 호출 수(쿼터)·시간별 호출빈도·
 * 쿼터 소진 예측을 계산한다(새 저장소 없음).
 *
 * 원천은 4종([TRACKED_PROVIDERS]) 고정 노출한다 — 로그가 0건인 원천도 total=0 으로 항상 나온다.
 * 쿼터는 data.go.kr 일 1000건 제한만 실재하므로 DATAGOKR 에만 [quotaLimit] 을 둔다.
 *
 * [hourlyTrend] 는 원천 무관 전체 호출량을 최근 24시간 1시간 단위로 버킷팅한다([RouteStatsService] 와 동일 방식·
 * 동일 [HourlyBucket] 재사용) — 관리자가 "외부 API 호출 횟수"를 시간축으로 본다.
 */
@Service
class ApiStatusService(
    private val apiCallLogStore: ApiCallLogStore,
) {

    fun summarize(): ApiStatusSummary {
        val now: Long = System.currentTimeMillis()
        val logs: List<ApiCallLog> = apiCallLogStore.findRecent(provider = null, limit = API_CALL_LOG_CAP)
        val todayStartMs: Long = todayStartMs(now)
        val byProvider: Map<String, List<ApiCallLog>> = logs.groupBy { it.provider }
        val providers: List<ProviderStatus> = TRACKED_PROVIDERS.map { provider ->
            aggregate(provider, byProvider[provider] ?: emptyList(), now, todayStartMs)
        }
        return ApiStatusSummary(
            providers = providers,
            windowSize = logs.size,
            hourlyTrend = hourlyBuckets(logs),
        )
    }

    /**
     * 최근 24시간을 1시간 단위로 버킷팅한다(오래된→최신, Asia/Seoul 정각 경계) — 넘겨준 로그 집합의 호출량.
     * 현재 시각을 Seoul 기준 정각으로 내림한 버킷을 마지막으로, 그 앞 23개를 채운다(호출이 0건인 시간도 노출).
     * 전체([ApiStatusSummary.hourlyTrend])와 원천별([ProviderStatus.hourlyCalls])이 **같은 함수·같은 경계**를 써
     * 프론트가 같은 축으로 겹쳐 그릴 수 있다. [RouteStatsService.hourlyTrend] 와도 같은 [HourlyBucket] 이다.
     */
    private fun hourlyBuckets(logs: List<ApiCallLog>): List<HourlyBucket> {
        val currentHour = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(SEOUL)
            .truncatedTo(ChronoUnit.HOURS)
        return (HOURLY_BUCKET_COUNT - 1 downTo 0).map { hoursAgo ->
            val bucketStart = currentHour.minusHours(hoursAgo.toLong())
            val startMs: Long = bucketStart.toInstant().toEpochMilli()
            val endMs: Long = bucketStart.plusHours(1).toInstant().toEpochMilli()
            val count: Int = logs.count { it.atEpochMs >= startMs && it.atEpochMs < endMs }
            HourlyBucket(hourStartMs = startMs, count = count)
        }
    }

    private fun aggregate(provider: String, logs: List<ApiCallLog>, nowMs: Long, todayStartMs: Long): ProviderStatus {
        val total: Int = logs.size
        val successCount: Int = logs.count { it.success }
        val successRate: Double = if (total == 0) {
            0.0
        } else {
            successCount.toDouble() / total
        }
        val avgDurationMs: Long = if (total == 0) {
            0L
        } else {
            (logs.sumOf { it.durationMs }.toDouble() / total).roundToLong()
        }
        val p95DurationMs: Long = Percentiles.nearestRank(logs.map { it.durationMs }.sorted(), 95)
        val todayCount: Int = logs.count { it.atEpochMs >= todayStartMs }
        val quotaLimit: Int? = QUOTA_LIMITS[provider]
        val quotaWarning: Boolean = quotaLimit != null && todayCount >= quotaLimit * QUOTA_WARNING_RATIO
        val quotaBurnPerHour: Double? = quotaBurnPerHour(quotaLimit, todayCount, nowMs, todayStartMs)
        return ProviderStatus(
            provider = provider,
            total = total,
            successCount = successCount,
            successRate = successRate,
            avgDurationMs = avgDurationMs,
            p95DurationMs = p95DurationMs,
            lastSuccessAtMs = logs.filter { it.success }.maxOfOrNull { it.atEpochMs },
            lastFailureAtMs = logs.filter { !it.success }.maxOfOrNull { it.atEpochMs },
            todayCount = todayCount,
            quotaLimit = quotaLimit,
            quotaWarning = quotaWarning,
            quotaBurnPerHour = quotaBurnPerHour,
            quotaProjectedTodayTotal = quotaProjectedTodayTotal(quotaBurnPerHour),
            hourlyCalls = hourlyBuckets(logs),
        )
    }

    /**
     * 오늘 경과시간 기준 시간당 쿼터 소진율(호출/시간) — 쿼터 없는 원천은 null.
     *
     * 근사: 자정부터 지금까지의 평균 속도가 하루 끝까지 유지된다고 본 선형 외삽이다(버스트·야간 편차 미반영).
     * 자정 직후 경과시간이 0 이하이면 속도를 잴 수 없어 0 으로 둔다(0 나눗셈 방지).
     */
    private fun quotaBurnPerHour(quotaLimit: Int?, todayCount: Int, nowMs: Long, todayStartMs: Long): Double? {
        if (quotaLimit == null) {
            return null
        }
        val elapsedHoursToday: Double = (nowMs - todayStartMs).toDouble() / HOUR_MS
        if (elapsedHoursToday <= 0.0) {
            return 0.0
        }
        return todayCount / elapsedHoursToday
    }

    /** 현재 소진율이 유지될 때 오늘 마감 예상 총 호출 수(반올림). 소진율이 null(쿼터 없음)이면 null. */
    private fun quotaProjectedTodayTotal(burnPerHour: Double?): Int? {
        if (burnPerHour == null) {
            return null
        }
        return (burnPerHour * HOURS_PER_DAY).roundToInt()
    }

    private fun todayStartMs(nowMs: Long): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    companion object {
        /** 항상 노출하는 원천 4종. 로그가 없어도 total=0 으로 나온다. */
        val TRACKED_PROVIDERS: List<String> = listOf("KAKAO", "TMAP", "DATAGOKR", "SAFEMAP")

        /** 원천별 일일 쿼터 상한(있는 것만) — data.go.kr 은 승인 기본 1일 1000건. */
        val QUOTA_LIMITS: Map<String, Int> = mapOf("DATAGOKR" to 1000)

        /** 오늘 호출 수가 일일 쿼터 상한의 이 비율 이상이면 경고(quotaWarning). */
        const val QUOTA_WARNING_RATIO: Double = 0.8

        /** 시간대 트렌드 버킷 수(최근 24시간, 1시간 단위) — [RouteStatsService] 와 동일. */
        const val HOURLY_BUCKET_COUNT: Int = 24

        /** 쿼터 소진율 계산용 1시간(ms). */
        const val HOUR_MS: Long = 3_600_000L

        /** 하루 시간 수 — 쿼터 마감 예상 총량 = 시간당 소진율 × 이 값. */
        const val HOURS_PER_DAY: Int = 24

        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}

/** 원천별 상태 요약 1건. */
data class ProviderStatus(
    val provider: String,
    val total: Int,
    val successCount: Int,
    /** 성공률 0.0~1.0. total=0 이면 0.0. */
    val successRate: Double,
    /** 평균 소요시간(ms, 반올림). total=0 이면 0. */
    val avgDurationMs: Long,
    /** 이 원천 그룹 내 소요시간 95 분위수(ms, nearest-rank). total=0 이면 0. */
    val p95DurationMs: Long,
    val lastSuccessAtMs: Long?,
    val lastFailureAtMs: Long?,
    /** 오늘(Asia/Seoul 자정 이후) 호출 수 — 쿼터 계측. */
    val todayCount: Int,
    /** 일일 쿼터 상한(없으면 null). */
    val quotaLimit: Int?,
    /** todayCount 가 상한의 80% 이상이면 경고. */
    val quotaWarning: Boolean,
    /**
     * 오늘 경과시간 기준 시간당 쿼터 소진율(호출/시간). 쿼터 있는 원천(DATAGOKR)만, 없으면 null.
     * 선형 외삽 근사 — 자정~현재 평균 속도가 유지된다는 가정.
     */
    val quotaBurnPerHour: Double?,
    /** 현재 소진율이 유지될 때 오늘 마감 예상 총 호출 수(반올림). 쿼터 없으면 null. */
    val quotaProjectedTodayTotal: Int?,
    /** 이 원천의 최근 24시간 시간별 호출빈도 — 전체 [ApiStatusSummary.hourlyTrend] 와 같은 경계·같은 버킷. */
    val hourlyCalls: List<HourlyBucket>,
)

/** api-status 응답 전체. [windowSize] 는 집계에 쓴 로그 총수, [hourlyTrend] 는 최근 24시간 전체 호출량 추이. */
data class ApiStatusSummary(
    val providers: List<ProviderStatus>,
    val windowSize: Int,
    val hourlyTrend: List<HourlyBucket>,
)
