package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.routestats.ROUTE_STATS_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 관리자 페이지용 "추천 통계" 집계 — 이미 저장된 추천 요청 기록([RouteStatsStore])만 읽어
 * 엔드포인트별 요청 수·안심강도 선호 분포·상위 목적지·최근 24시간 시간대 트렌드를 계산한다(새 저장소 없음).
 *
 * [ApiStatusService] 와 같은 방식 — 창(window)은 최근 [ROUTE_STATS_CAP] 건이고, 시각 처리는 Asia/Seoul 기준이다.
 * 엔드포인트는 2종([TRACKED_ENDPOINTS]) 고정 노출한다(요청이 0건인 엔드포인트도 count=0 으로 항상 나온다).
 */
@Service
class RouteStatsService(
    private val routeStatsStore: RouteStatsStore,
) {

    fun summarize(): RouteStatsSummary {
        val logs = routeStatsStore.findRecent(ROUTE_STATS_CAP)
        return RouteStatsSummary(
            total = logs.size,
            windowSize = logs.size,
            byEndpoint = byEndpoint(logs),
            byPreference = byPreference(logs),
            topDestinations = topDestinations(logs),
            hourlyTrend = hourlyTrend(logs),
        )
    }

    /** 엔드포인트 2종 고정 노출(compare/safe-compare) — 요청이 없어도 count=0 으로 나온다. */
    private fun byEndpoint(logs: List<RouteRecommendationLog>): List<EndpointCount> {
        val counts = logs.groupingBy { it.endpoint }.eachCount()
        return TRACKED_ENDPOINTS.map { endpoint ->
            EndpointCount(endpoint = endpoint, count = counts[endpoint] ?: 0)
        }
    }

    /** 안심강도 선호 분포 — 실제 나타난 값만, 많은 순. */
    private fun byPreference(logs: List<RouteRecommendationLog>): List<PreferenceCount> =
        logs.groupingBy { it.preference }.eachCount()
            .map { (preference, count) -> PreferenceCount(preference = preference, count = count) }
            .sortedByDescending { it.count }

    /** 상위 목적지 — 빈 이름 제외, 많은 순, 최대 [TOP_DESTINATIONS_LIMIT] 건. */
    private fun topDestinations(logs: List<RouteRecommendationLog>): List<DestinationCount> =
        logs.asSequence()
            .mapNotNull { it.destinationName?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(TOP_DESTINATIONS_LIMIT)
            .map { DestinationCount(name = it.key, count = it.value) }

    /**
     * 최근 24시간을 1시간 단위로 버킷팅한다(오래된→최신, Asia/Seoul 정각 경계).
     * 현재 시각을 Seoul 기준 정각으로 내림한 버킷을 마지막으로, 그 앞 23개를 채운다 — 요청이 0건인 시간도 노출한다.
     */
    private fun hourlyTrend(logs: List<RouteRecommendationLog>): List<HourlyBucket> {
        val currentHour = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(SEOUL)
            .truncatedTo(ChronoUnit.HOURS)
        return (HOURLY_BUCKET_COUNT - 1 downTo 0).map { hoursAgo ->
            val bucketStart = currentHour.minusHours(hoursAgo.toLong())
            val startMs = bucketStart.toInstant().toEpochMilli()
            val endMs = bucketStart.plusHours(1).toInstant().toEpochMilli()
            val count = logs.count { it.atEpochMs >= startMs && it.atEpochMs < endMs }
            HourlyBucket(hourStartMs = startMs, count = count)
        }
    }

    companion object {
        /** 항상 노출하는 추천 엔드포인트 2종. 요청이 없어도 count=0 으로 나온다. */
        val TRACKED_ENDPOINTS = listOf("compare", "safe-compare")

        /** 상위 목적지 최대 노출 건수. */
        const val TOP_DESTINATIONS_LIMIT = 10

        /** 시간대 트렌드 버킷 수(최근 24시간, 1시간 단위). */
        const val HOURLY_BUCKET_COUNT = 24

        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}

/** 엔드포인트별 추천 요청 수 1건. */
data class EndpointCount(
    /** compare | safe-compare. */
    val endpoint: String,
    val count: Int,
)

/** 안심강도 선호별 요청 수 1건. */
data class PreferenceCount(
    /** SafetyPreference.requestValue: balanced | safe | safest. */
    val preference: String,
    val count: Int,
)

/** 목적지별 추천 요청 수 1건(상위 노출용). */
data class DestinationCount(
    val name: String,
    val count: Int,
)

/** 시간대 트렌드 버킷 1건. [hourStartMs] 는 해당 1시간 버킷의 시작(epoch ms, Asia/Seoul 정각). */
data class HourlyBucket(
    val hourStartMs: Long,
    val count: Int,
)

/**
 * route-stats 응답 전체. [total] 은 창 내 총 추천 요청 수, [windowSize] 는 집계에 쓴 기록 수(둘 다 창 상한 이하).
 */
data class RouteStatsSummary(
    val total: Int,
    val windowSize: Int,
    val byEndpoint: List<EndpointCount>,
    val byPreference: List<PreferenceCount>,
    val topDestinations: List<DestinationCount>,
    val hourlyTrend: List<HourlyBucket>,
)
