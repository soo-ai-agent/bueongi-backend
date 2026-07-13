package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.apilog.API_CALL_LOG_CAP
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * 관리자 페이지용 "외부 API 상태 요약·쿼터" 집계 — 이미 저장된 접속 내역([ApiCallLogStore])만 읽어
 * 원천(provider)별로 성공률·평균 소요시간·최근 성공/실패 시각·오늘 호출 수(쿼터)를 계산한다(새 저장소 없음).
 *
 * 원천은 4종([TRACKED_PROVIDERS]) 고정 노출한다 — 로그가 0건인 원천도 total=0 으로 항상 나온다.
 * 쿼터는 data.go.kr 일 1000건 제한만 실재하므로 DATAGOKR 에만 [quotaLimit] 을 둔다.
 */
@Service
class ApiStatusService(
    private val apiCallLogStore: ApiCallLogStore,
) {

    fun summarize(): ApiStatusSummary {
        val logs = apiCallLogStore.findRecent(provider = null, limit = API_CALL_LOG_CAP)
        val todayStartMs = todayStartMs()
        val byProvider = logs.groupBy { it.provider }
        val providers = TRACKED_PROVIDERS.map { provider ->
            aggregate(provider, byProvider[provider] ?: emptyList(), todayStartMs)
        }
        return ApiStatusSummary(providers = providers, windowSize = logs.size)
    }

    private fun aggregate(provider: String, logs: List<ApiCallLog>, todayStartMs: Long): ProviderStatus {
        val total = logs.size
        val successCount = logs.count { it.success }
        val successRate = if (total == 0) {
            0.0
        } else {
            successCount.toDouble() / total
        }
        val avgDurationMs = if (total == 0) {
            0L
        } else {
            (logs.sumOf { it.durationMs }.toDouble() / total).roundToLong()
        }
        val todayCount = logs.count { it.atEpochMs >= todayStartMs }
        val quotaLimit = QUOTA_LIMITS[provider]
        val quotaWarning = quotaLimit != null && todayCount >= quotaLimit * QUOTA_WARNING_RATIO
        return ProviderStatus(
            provider = provider,
            total = total,
            successCount = successCount,
            successRate = successRate,
            avgDurationMs = avgDurationMs,
            lastSuccessAtMs = logs.filter { it.success }.maxOfOrNull { it.atEpochMs },
            lastFailureAtMs = logs.filter { !it.success }.maxOfOrNull { it.atEpochMs },
            todayCount = todayCount,
            quotaLimit = quotaLimit,
            quotaWarning = quotaWarning,
        )
    }

    private fun todayStartMs(): Long {
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    companion object {
        /** 항상 노출하는 원천 4종. 로그가 없어도 total=0 으로 나온다. */
        val TRACKED_PROVIDERS = listOf("KAKAO", "TMAP", "DATAGOKR", "SAFEMAP")

        /** 원천별 일일 쿼터 상한(있는 것만) — data.go.kr 은 승인 기본 1일 1000건. */
        val QUOTA_LIMITS = mapOf("DATAGOKR" to 1000)

        /** 오늘 호출 수가 일일 쿼터 상한의 이 비율 이상이면 경고(quotaWarning). */
        const val QUOTA_WARNING_RATIO = 0.8

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
    val lastSuccessAtMs: Long?,
    val lastFailureAtMs: Long?,
    /** 오늘(Asia/Seoul 자정 이후) 호출 수 — 쿼터 계측. */
    val todayCount: Int,
    /** 일일 쿼터 상한(없으면 null). */
    val quotaLimit: Int?,
    /** todayCount 가 상한의 80% 이상이면 경고. */
    val quotaWarning: Boolean,
)

/** api-status 응답 전체. [windowSize] 는 집계에 쓴 로그 총수. */
data class ApiStatusSummary(
    val providers: List<ProviderStatus>,
    val windowSize: Int,
)
