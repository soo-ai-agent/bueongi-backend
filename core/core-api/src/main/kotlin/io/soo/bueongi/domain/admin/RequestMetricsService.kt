package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * 관리자 페이지용 "수신 트래픽" 집계 — 이미 저장된 수신 요청 기록([RequestMetricStore])만 읽어
 * 엔드포인트별 호출수와 **예측 사용자 수**(고유 익명 client-id 관측치)를 계산한다(새 저장소 없음).
 *
 * [ApiStatusService]·[RouteStatsService] 와 같은 관례 — 창(window)은 최근 [REQUEST_METRIC_CAP] 건이고
 * 시각 처리는 Asia/Seoul 기준, 시간대 추이는 24버킷 [HourlyBucket] 을 재사용한다.
 *
 * 사용자 추정은 **관측치**임을 정직하게 드러낸다 — `clientId` 가 없는 요청은 고유 사용자 집계에서 제외하되
 * 호출수([EndpointTraffic.count])엔 포함하고, 제외된 호출 수를 [EstimatedUsers.callsWithoutClientId] 로 함께 노출한다.
 */
@Service
class RequestMetricsService(
    private val requestMetricStore: RequestMetricStore,
) {

    fun summarize(): TrafficSummary {
        val metrics = requestMetricStore.findRecent(REQUEST_METRIC_CAP)
        return TrafficSummary(
            totalCalls = metrics.size,
            windowSize = metrics.size,
            byEndpoint = byEndpoint(metrics),
            estimatedUsers = estimatedUsers(metrics),
            hourlyCalls = hourlyCalls(metrics),
            dailyActiveUsers = dailyActiveUsers(metrics),
        )
    }

    /** 메서드+정규화경로 단위로 묶어 호출수·성공률·평균 소요·고유 클라이언트 수를 집계한다(호출수 내림차순). */
    private fun byEndpoint(metrics: List<RequestMetric>): List<EndpointTraffic> =
        metrics.groupBy { EndpointKey(method = it.method, endpoint = it.endpoint) }
            .map { (key, group) -> aggregateEndpoint(key, group) }
            .sortedByDescending { it.count }

    private fun aggregateEndpoint(key: EndpointKey, group: List<RequestMetric>): EndpointTraffic {
        val count = group.size // groupBy 결과라 최소 1건 — 0 나눗셈 없음
        val successCount = group.count { it.status in SUCCESS_STATUS_RANGE }
        val successRate = successCount.toDouble() / count
        val avgDurationMs = (group.sumOf { it.durationMs }.toDouble() / count).roundToLong()
        return EndpointTraffic(
            method = key.method,
            endpoint = key.endpoint,
            count = count,
            successRate = successRate,
            avgDurationMs = avgDurationMs,
            distinctClients = distinctClients(group),
        )
    }

    /**
     * 예측 사용자 수 — 고유 익명 clientId 기준. today 는 Asia/Seoul 자정 이후, last7d/last30d 는 현재로부터의
     * 이동 창(rolling)이다. clientId 없는 요청은 고유 집계에서 빠지되 그 수를 [EstimatedUsers.callsWithoutClientId] 로 남긴다.
     */
    private fun estimatedUsers(metrics: List<RequestMetric>): EstimatedUsers {
        val now = System.currentTimeMillis()
        return EstimatedUsers(
            today = distinctClientsSince(metrics, todayStartMs(now)),
            last7d = distinctClientsSince(metrics, now - DAYS_7_MS),
            last30d = distinctClientsSince(metrics, now - DAYS_30_MS),
            callsWithoutClientId = metrics.count { it.clientId == null },
        )
    }

    /** [sinceMs] 이후 요청의 고유 clientId 수(null 제외). */
    private fun distinctClientsSince(metrics: List<RequestMetric>, sinceMs: Long): Int =
        distinctClients(metrics.filter { it.atEpochMs >= sinceMs })

    private fun distinctClients(metrics: List<RequestMetric>): Int =
        metrics.mapNotNull { it.clientId }.distinct().size

    /**
     * 최근 24시간을 1시간 단위로 버킷팅한 호출수(오래된→최신, Asia/Seoul 정각 경계).
     * [ApiStatusService.hourlyTrend]·[RouteStatsService] 와 같은 경계·같은 [HourlyBucket] 이라 프론트 막대 렌더를 공유한다.
     */
    private fun hourlyCalls(metrics: List<RequestMetric>): List<HourlyBucket> {
        val currentHour = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(SEOUL)
            .truncatedTo(ChronoUnit.HOURS)
        return (HOURLY_BUCKET_COUNT - 1 downTo 0).map { hoursAgo ->
            val bucketStart = currentHour.minusHours(hoursAgo.toLong())
            val startMs = bucketStart.toInstant().toEpochMilli()
            val endMs = bucketStart.plusHours(1).toInstant().toEpochMilli()
            val count = metrics.count { it.atEpochMs >= startMs && it.atEpochMs < endMs }
            HourlyBucket(hourStartMs = startMs, count = count)
        }
    }

    /**
     * 최근 [DAILY_ACTIVE_DAYS] 일의 일자별 고유 사용자 수(오래된→최신, Asia/Seoul 자정 경계).
     * 각 버킷의 [DailyActive.dateEpochMs] 는 해당 날 자정(epoch ms), 요청 0건 날도 distinctClients=0 으로 노출한다.
     */
    private fun dailyActiveUsers(metrics: List<RequestMetric>): List<DailyActive> {
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(SEOUL).toLocalDate()
        return (DAILY_ACTIVE_DAYS - 1 downTo 0).map { daysAgo ->
            val dayStart = today.minusDays(daysAgo.toLong()).atStartOfDay(SEOUL)
            val startMs = dayStart.toInstant().toEpochMilli()
            val endMs = dayStart.plusDays(1).toInstant().toEpochMilli()
            val ofDay = metrics.filter { it.atEpochMs >= startMs && it.atEpochMs < endMs }
            DailyActive(dateEpochMs = startMs, distinctClients = distinctClients(ofDay))
        }
    }

    private fun todayStartMs(nowMs: Long): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    /** byEndpoint 집계 키 — 같은 경로라도 메서드가 다르면 별도 집계(GET 조회 vs POST 갱신). */
    private data class EndpointKey(
        val method: String,
        val endpoint: String,
    )

    companion object {
        /** 성공으로 보는 status 범위(2xx·3xx). */
        val SUCCESS_STATUS_RANGE = 200..399

        /** 시간대 추이 버킷 수(최근 24시간, 1시간 단위) — [ApiStatusService]·[RouteStatsService] 와 동일. */
        const val HOURLY_BUCKET_COUNT = 24

        /** 일자별 활성 사용자 노출 일수(최근 7일). */
        const val DAILY_ACTIVE_DAYS = 7

        /** last7d 이동 창 길이(ms). */
        const val DAYS_7_MS = 7L * 24 * 60 * 60 * 1000

        /** last30d 이동 창 길이(ms). */
        const val DAYS_30_MS = 30L * 24 * 60 * 60 * 1000

        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}

/** 엔드포인트(메서드+정규화경로)별 트래픽 1건. */
data class EndpointTraffic(
    /** HTTP 메서드: GET | POST | … */
    val method: String,
    /** 정규화된 경로 템플릿: 예 `/share/{token}/location`. */
    val endpoint: String,
    /** 창 내 호출 수(clientId 유무 무관 전량). */
    val count: Int,
    /** 성공률 0.0~1.0 — status 가 2xx·3xx 인 비율. */
    val successRate: Double,
    /** 평균 소요시간(ms, 반올림). */
    val avgDurationMs: Long,
    /** 이 엔드포인트를 부른 고유 익명 클라이언트 수(clientId null 제외). */
    val distinctClients: Int,
)

/**
 * 예측 사용자 수 — 고유 익명 client-id 기반 **관측치**(로그인 사용자 수가 아니다).
 * clientId 없는 요청은 [today]/[last7d]/[last30d] 에서 제외되며, 그 수는 [callsWithoutClientId] 로 정직하게 노출한다.
 */
data class EstimatedUsers(
    /** 오늘(Asia/Seoul 자정 이후) 고유 clientId 수. */
    val today: Int,
    /** 최근 7일(이동 창) 고유 clientId 수. */
    val last7d: Int,
    /** 최근 30일(이동 창) 고유 clientId 수. */
    val last30d: Int,
    /** clientId 가 없어 사용자 추정에서 제외된 호출 수(정직성 지표). */
    val callsWithoutClientId: Int,
)

/** 일자별 활성 사용자 1건. [dateEpochMs] 는 해당 날 자정(Asia/Seoul, epoch ms). */
data class DailyActive(
    val dateEpochMs: Long,
    val distinctClients: Int,
)

/**
 * traffic 응답 전체. [totalCalls] 는 창 내 총 수신 호출 수, [windowSize] 는 집계에 쓴 기록 수(둘 다 창 상한 이하).
 * [estimatedUsers] 는 고유 익명 client-id 관측치다(실제 사용자 수 추정이 아니라 관측 하한).
 */
data class TrafficSummary(
    val totalCalls: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointTraffic>,
    val estimatedUsers: EstimatedUsers,
    val hourlyCalls: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
)
