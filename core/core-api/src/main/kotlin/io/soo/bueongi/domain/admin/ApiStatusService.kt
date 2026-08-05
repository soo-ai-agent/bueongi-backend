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

/**
 * 관리자 페이지용 "외부 API 접속 내역" 도메인 — 저장된 계측([ApiCallLogStore])을 소유해 목록 조회([listRecentCalls])와
 * 상태 요약([summarize])을 함께 제공한다. 요약은 원천([ApiProvider])별로 성공률·평균/95분위 소요시간·
 * 최근 성공/실패 시각·오늘 호출 수(쿼터)·시간별 호출빈도·쿼터 소진 예측을 계산한다(새 저장소 없음).
 *
 * 노출 원천은 4종([TRACKED_PROVIDERS]) 고정이다 — 로그가 0건인 원천도 total=0 으로 항상 나오고,
 * 계측만 하고 화면에는 싣지 않는 원천(SAFETYDATA)은 여기 없다(응답 계약 유지).
 * 쿼터는 data.go.kr 일 1000건 제한만 실재하므로 DATAGOKR 에만 [quotaLimit] 을 둔다.
 *
 * [hourlyTrend] 는 원천 무관 전체 호출량을 최근 24시간 1시간 단위로 버킷팅한다
 * (경계는 [HourlyBuckets] 단일 정의) — 관리자가 "외부 API 호출 횟수"를 시간축으로 본다.
 */
@Service
class ApiStatusService(
    private val apiCallLogStore: ApiCallLogStore,
) {

    /**
     * 외부 API 접속 내역 목록(최근순) — 관리자 화면의 원본 로그 뷰.
     * 컨트롤러가 저장소를 직접 주입받지 않도록 접속 내역을 소유한 이 도메인이 조회를 중계한다.
     *
     * [provider] 는 화면 필터(자유 문자열)라 [ApiProvider] 로 좁히지 않는다 — 알 수 없는 값이면 빈 목록이 되고,
     * 오타 섞인 필터가 4xx/5xx 로 번지지 않는다(기존 동작 보존). null 이면 전체.
     */
    fun listRecentCalls(provider: String?, limit: Int): List<ApiCallLog> =
        apiCallLogStore.findRecent(provider = provider, limit = limit)

    fun summarize(): ApiStatusSummary {
        val now: Long = System.currentTimeMillis()
        val logs: List<ApiCallLog> = apiCallLogStore.findRecent(provider = null, limit = API_CALL_LOG_CAP)
        val todayStartMs: Long = todayStartMs(now)
        val byProvider: Map<String, List<ApiCallLog>> = logs.groupBy { it.provider }
        val providers: List<ProviderStatus> = TRACKED_PROVIDERS.map { provider ->
            aggregate(provider, byProvider[provider.name] ?: emptyList(), now, todayStartMs)
        }
        return ApiStatusSummary(
            providers = providers,
            windowSize = logs.size,
            hourlyTrend = hourlyBuckets(logs),
        )
    }

    /**
     * 넘겨준 로그 집합의 최근 24시간 시간별 호출량 — 버킷 경계는 [HourlyBuckets] 가 단일 정의한다.
     * 전체([ApiStatusSummary.hourlyTrend])와 원천별([ProviderStatus.hourlyCalls])이 **같은 경계**를 써
     * 프론트가 같은 축으로 겹쳐 그릴 수 있다.
     */
    private fun hourlyBuckets(logs: List<ApiCallLog>): List<HourlyBucket> =
        HourlyBuckets.countOf(logs.map { it.atEpochMs })

    private fun aggregate(provider: ApiProvider, logs: List<ApiCallLog>, nowMs: Long, todayStartMs: Long): ProviderStatus {
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
            provider = provider.name,
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
        val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    companion object {
        /**
         * 항상 노출하는 원천 4종(응답 순서도 이 순서). 로그가 없어도 total=0 으로 나온다.
         * [ApiProvider] 전체가 아니라 **명시 목록**이다 — 계측만 하고 이 화면엔 싣지 않는 원천(SAFETYDATA)이 있어,
         * enum 에 항목이 늘어도 응답 필드 수가 조용히 변하지 않게 한다(계약 고정).
         */
        val TRACKED_PROVIDERS: List<ApiProvider> = listOf(
            ApiProvider.KAKAO,
            ApiProvider.TMAP,
            ApiProvider.DATAGOKR,
            ApiProvider.SAFEMAP,
        )

        /** 원천별 일일 쿼터 상한(있는 것만) — data.go.kr 은 승인 기본 1일 1000건. */
        val QUOTA_LIMITS: Map<ApiProvider, Int> = mapOf(ApiProvider.DATAGOKR to 1000)

        /** 오늘 호출 수가 일일 쿼터 상한의 이 비율 이상이면 경고(quotaWarning). */
        const val QUOTA_WARNING_RATIO: Double = 0.8

        /** 쿼터 소진율 계산용 1시간(ms). */
        const val HOUR_MS: Long = 3_600_000L

        /** 하루 시간 수 — 쿼터 마감 예상 총량 = 시간당 소진율 × 이 값. */
        const val HOURS_PER_DAY: Int = 24

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
