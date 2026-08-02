package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.requestmetric.REQUEST_METRIC_CAP
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/**
 * 관리자 페이지용 "수신 트래픽" 집계 — 이미 저장된 수신 요청 기록([RequestMetricStore])만 읽어
 * 엔드포인트별 호출수·소요시간 분위수와 **예측 사용자 수**(고유 익명 client-id 관측치, 신규/재방문 근사)·
 * 시간대별 에러 건수를 계산한다(새 저장소 없음).
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
        val metrics: List<RequestMetric> = requestMetricStore.findRecent(REQUEST_METRIC_CAP)
        return TrafficSummary(
            totalCalls = metrics.size,
            windowSize = metrics.size,
            byEndpoint = byEndpoint(metrics),
            estimatedUsers = estimatedUsers(metrics),
            latency = latency(metrics),
            hourlyCalls = hourlyCalls(metrics),
            hourlyErrors = hourlyErrors(metrics),
            dailyActiveUsers = dailyActiveUsers(metrics),
        )
    }

    /** 전체 소요시간 분위수(p50/p95/p99) — 정렬 1회 후 [Percentiles.nearestRank] 로 뽑는다. 빈 창이면 전부 0. */
    private fun latency(metrics: List<RequestMetric>): LatencyPercentiles {
        val sortedDurations: List<Long> = metrics.map { it.durationMs }.sorted()
        return LatencyPercentiles(
            p50Ms = Percentiles.nearestRank(sortedDurations, 50),
            p95Ms = Percentiles.nearestRank(sortedDurations, 95),
            p99Ms = Percentiles.nearestRank(sortedDurations, 99),
        )
    }

    /** 메서드+정규화경로 단위로 묶어 호출수·성공률·평균 소요·고유 클라이언트 수를 집계한다(호출수 내림차순). */
    private fun byEndpoint(metrics: List<RequestMetric>): List<EndpointTraffic> =
        metrics.groupBy { EndpointKey(method = it.method, endpoint = it.endpoint) }
            .map { (key, group) -> aggregateEndpoint(key, group) }
            .sortedByDescending { it.count }

    private fun aggregateEndpoint(key: EndpointKey, group: List<RequestMetric>): EndpointTraffic {
        val count: Int = group.size // groupBy 결과라 최소 1건 — 0 나눗셈 없음
        val successCount: Int = group.count { it.status in SUCCESS_STATUS_RANGE }
        val successRate: Double = successCount.toDouble() / count
        val avgDurationMs: Long = (group.sumOf { it.durationMs }.toDouble() / count).roundToLong()
        val p95DurationMs: Long = Percentiles.nearestRank(group.map { it.durationMs }.sorted(), 95)
        return EndpointTraffic(
            method = key.method,
            endpoint = key.endpoint,
            count = count,
            successRate = successRate,
            avgDurationMs = avgDurationMs,
            p95DurationMs = p95DurationMs,
            distinctClients = distinctClients(group),
        )
    }

    /**
     * 예측 사용자 수 — 고유 익명 clientId 기준. today 는 Asia/Seoul 자정 이후, last7d/last30d 는 현재로부터의
     * 이동 창(rolling)이다. clientId 없는 요청은 고유 집계에서 빠지되 그 수를 [EstimatedUsers.callsWithoutClientId] 로 남긴다.
     *
     * newToday/returningToday 는 오늘 활동한 clientId 를 **창 내 최초 관측 시각** 기준으로 신규/재방문으로 가른다(아래 [newVsReturningToday]).
     */
    private fun estimatedUsers(metrics: List<RequestMetric>): EstimatedUsers {
        val now: Long = System.currentTimeMillis()
        val todayStartMs: Long = todayStartMs(now)
        val newVsReturning: NewVsReturning = newVsReturningToday(metrics, todayStartMs)
        return EstimatedUsers(
            today = distinctClientsSince(metrics, todayStartMs),
            last7d = distinctClientsSince(metrics, now - DAYS_7_MS),
            last30d = distinctClientsSince(metrics, now - DAYS_30_MS),
            callsWithoutClientId = metrics.count { it.clientId == null },
            newToday = newVsReturning.newToday,
            returningToday = newVsReturning.returningToday,
        )
    }

    /**
     * 오늘 활동한 clientId 를 신규/재방문으로 가른다 — **창(CAP) 내 최초 관측 시각**이 오늘 자정 이후면 신규, 이전이면 재방문.
     *
     * 근사임을 분명히: 진짜 "첫 방문"이 아니라 이 창에 남아 있는 기록 중 가장 이른 것 기준이다.
     * 창을 벗어난 과거 방문은 알 수 없어, 오래 전 사용자가 창에서 밀려나면 신규로 잡힐 수 있다.
     */
    private fun newVsReturningToday(metrics: List<RequestMetric>, todayStartMs: Long): NewVsReturning {
        val firstSeenByClient: Map<String, Long> = firstSeenByClient(metrics)
        val activeTodayClients: List<String> = metrics
            .filter { it.atEpochMs >= todayStartMs }
            .mapNotNull { it.clientId }
            .distinct()
        val newCount: Int = activeTodayClients.count { clientId ->
            val firstSeen: Long = firstSeenByClient[clientId] ?: todayStartMs
            firstSeen >= todayStartMs
        }
        return NewVsReturning(newToday = newCount, returningToday = activeTodayClients.size - newCount)
    }

    /** 창 내 각 clientId 의 최초 관측 시각(epoch ms). clientId 없는 요청은 제외. */
    private fun firstSeenByClient(metrics: List<RequestMetric>): Map<String, Long> {
        val firstSeen: MutableMap<String, Long> = mutableMapOf<String, Long>()
        metrics.forEach { metric ->
            val clientId: String = metric.clientId ?: return@forEach
            val previous: Long? = firstSeen[clientId]
            if (previous == null || metric.atEpochMs < previous) {
                firstSeen[clientId] = metric.atEpochMs
            }
        }
        return firstSeen
    }

    /** [sinceMs] 이후 요청의 고유 clientId 수(null 제외). */
    private fun distinctClientsSince(metrics: List<RequestMetric>, sinceMs: Long): Int =
        distinctClients(metrics.filter { it.atEpochMs >= sinceMs })

    private fun distinctClients(metrics: List<RequestMetric>): Int =
        metrics.mapNotNull { it.clientId }.distinct().size

    /**
     * 최근 24시간 시간대별 수신 호출수 — 버킷 경계는 [HourlyBuckets] 가 단일 정의한다.
     * 다른 관리자 지표와 같은 경계·같은 [HourlyBucket] 이라 프론트 막대 렌더를 공유한다.
     */
    private fun hourlyCalls(metrics: List<RequestMetric>): List<HourlyBucket> =
        HourlyBuckets.countOf(metrics.map { it.atEpochMs })

    /**
     * 최근 24시간 시간대별 **에러 건수**(2xx·3xx 가 아닌 status). [hourlyCalls] 와 같은 경계·같은 [HourlyBucket] 이라
     * 프론트가 호출량 막대 위에 에러를 겹쳐 그릴 수 있다(에러 0건 시간도 노출).
     */
    private fun hourlyErrors(metrics: List<RequestMetric>): List<HourlyBucket> {
        val errorTimestampsMs: List<Long> = metrics
            .filter { it.status !in SUCCESS_STATUS_RANGE }
            .map { it.atEpochMs }
        return HourlyBuckets.countOf(errorTimestampsMs)
    }

    /**
     * 최근 [DAILY_ACTIVE_DAYS] 일의 일자별 고유 사용자 수(오래된→최신, Asia/Seoul 자정 경계).
     * 각 버킷의 [DailyActive.dateEpochMs] 는 해당 날 자정(epoch ms), 요청 0건 날도 distinctClients=0 으로 노출한다.
     */
    private fun dailyActiveUsers(metrics: List<RequestMetric>): List<DailyActive> {
        val today: LocalDate = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(SEOUL).toLocalDate()
        return (DAILY_ACTIVE_DAYS - 1 downTo 0).map { daysAgo ->
            val dayStart: ZonedDateTime = today.minusDays(daysAgo.toLong()).atStartOfDay(SEOUL)
            val startMs: Long = dayStart.toInstant().toEpochMilli()
            val endMs: Long = dayStart.plusDays(1).toInstant().toEpochMilli()
            val ofDay: List<RequestMetric> = metrics.filter { it.atEpochMs >= startMs && it.atEpochMs < endMs }
            DailyActive(dateEpochMs = startMs, distinctClients = distinctClients(ofDay))
        }
    }

    private fun todayStartMs(nowMs: Long): Long {
        val today: LocalDate = Instant.ofEpochMilli(nowMs).atZone(SEOUL).toLocalDate()
        return today.atStartOfDay(SEOUL).toInstant().toEpochMilli()
    }

    /** byEndpoint 집계 키 — 같은 경로라도 메서드가 다르면 별도 집계(GET 조회 vs POST 갱신). */
    private data class EndpointKey(
        val method: String,
        val endpoint: String,
    )

    /** 오늘 활동한 clientId 의 신규/재방문 분해 결과(서비스 내부 전달용). */
    private data class NewVsReturning(
        val newToday: Int,
        val returningToday: Int,
    )

    companion object {
        /** 성공으로 보는 status 범위(2xx·3xx). */
        val SUCCESS_STATUS_RANGE: IntRange = 200..399

        /** 일자별 활성 사용자 노출 일수(최근 7일). */
        const val DAILY_ACTIVE_DAYS: Int = 7

        /** last7d 이동 창 길이(ms). */
        const val DAYS_7_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** last30d 이동 창 길이(ms). */
        const val DAYS_30_MS: Long = 30L * 24 * 60 * 60 * 1000

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
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
    /** 이 엔드포인트 그룹 내 소요시간 95 분위수(ms, nearest-rank). 빈 그룹은 없음(groupBy 결과라 최소 1건). */
    val p95DurationMs: Long,
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
    /**
     * 오늘 활동한 clientId 중 **창(CAP) 내 최초 관측이 오늘**인 수(신규 근사).
     * 창 밖 과거 방문은 알 수 없어, 오래된 사용자가 창에서 밀려나면 신규로 잡힐 수 있는 관측 근사다.
     */
    val newToday: Int,
    /** 오늘 활동한 clientId 중 **창 내 최초 관측이 오늘 이전**인 수(재방문 근사). [today] = [newToday] + [returningToday]. */
    val returningToday: Int,
)

/** 소요시간 백분위수(ms) 묶음 — nearest-rank(보간 없음). 빈 창이면 전부 0. */
data class LatencyPercentiles(
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
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
    /** 전체 소요시간 분위수(p50/p95/p99, ms). */
    val latency: LatencyPercentiles,
    val hourlyCalls: List<HourlyBucket>,
    /** 최근 24시간 시간대별 에러(2xx·3xx 아님) 건수 — [hourlyCalls] 와 같은 경계·같은 버킷. */
    val hourlyErrors: List<HourlyBucket>,
    val dailyActiveUsers: List<DailyActive>,
)
