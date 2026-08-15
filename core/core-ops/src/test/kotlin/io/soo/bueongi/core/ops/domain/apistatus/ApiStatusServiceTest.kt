package io.soo.bueongi.core.ops.domain.apistatus

import io.soo.bueongi.support.util.HourlyBucket

import io.soo.bueongi.core.ops.domain.apistatus.ApiStatusService
import io.soo.bueongi.core.ops.domain.apistatus.ApiStatusSummary
import io.soo.bueongi.core.ops.domain.apistatus.ProviderStatus

import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.math.roundToInt

class ApiStatusServiceTest {

    private fun store(logs: List<ApiCallLog>): ApiCallLogStore = object : ApiCallLogStore {
        override fun record(log: ApiCallLog) = Unit
        override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> = logs.take(limit)
    }

    private val seoulNow: Long = System.currentTimeMillis()
    private val todayStart: Long = java.time.Instant.ofEpochMilli(seoulNow)
        .atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
        .atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()

    @Test
    fun `성공 실패 섞인 로그로 성공률 평균 최근시각을 집계한다`() {
        val logs: List<ApiCallLog> = listOf(
            ApiCallLog(atEpochMs = 300, provider = "KAKAO", endpoint = "search", success = true, durationMs = 100),
            ApiCallLog(atEpochMs = 200, provider = "KAKAO", endpoint = "search", success = false, durationMs = 200, detail = "boom"),
            ApiCallLog(atEpochMs = 100, provider = "KAKAO", endpoint = "search", success = true, durationMs = 300),
        )

        val kakao: ProviderStatus = ApiStatusService(store(logs)).summarize().providers.first { it.provider == "KAKAO" }

        assertThat(kakao.total).isEqualTo(3)
        assertThat(kakao.successCount).isEqualTo(2)
        assertThat(kakao.successRate).isEqualTo(2.0 / 3.0)
        assertThat(kakao.avgDurationMs).isEqualTo(200L)
        assertThat(kakao.lastSuccessAtMs).isEqualTo(300L)
        assertThat(kakao.lastFailureAtMs).isEqualTo(200L)
    }

    @Test
    fun `DATAGOKR 오늘 800건 이상이면 쿼터 경고 어제 것은 제외`() {
        val today: List<ApiCallLog> = (1..820).map {
            ApiCallLog(atEpochMs = todayStart + 1000, provider = "DATAGOKR", endpoint = "cctv", success = true, durationMs = 10)
        }
        val yesterday: List<ApiCallLog> = (1..50).map {
            ApiCallLog(atEpochMs = todayStart - 1000, provider = "DATAGOKR", endpoint = "cctv", success = true, durationMs = 10)
        }

        val datagokr: ProviderStatus = ApiStatusService(store(today + yesterday)).summarize().providers.first { it.provider == "DATAGOKR" }

        assertThat(datagokr.todayCount).isEqualTo(820)
        assertThat(datagokr.quotaLimit).isEqualTo(1000)
        assertThat(datagokr.quotaWarning).isTrue()
    }

    @Test
    fun `p95DurationMs 는 원천 그룹 내 소요시간 분위수이고 로그 없는 원천은 0 이다`() {
        val logs: List<ApiCallLog> = (1..20).map {
            ApiCallLog(atEpochMs = 100, provider = "KAKAO", endpoint = "search", success = true, durationMs = (it * 10).toLong())
        }

        val summary: ApiStatusSummary = ApiStatusService(store(logs)).summarize()

        assertThat(summary.providers.first { it.provider == "KAKAO" }.p95DurationMs).isEqualTo(190L)
        assertThat(summary.providers.first { it.provider == "TMAP" }.p95DurationMs).isEqualTo(0L)
    }

    @Test
    fun `hourlyCalls 는 원천별 24버킷이며 방금 호출은 마지막 버킷에 원천별로 집계된다`() {
        val logs: List<ApiCallLog> = listOf(
            ApiCallLog(atEpochMs = seoulNow, provider = "KAKAO", endpoint = "search", success = true, durationMs = 10),
            ApiCallLog(atEpochMs = seoulNow, provider = "KAKAO", endpoint = "search", success = true, durationMs = 20),
            ApiCallLog(atEpochMs = seoulNow, provider = "TMAP", endpoint = "route", success = true, durationMs = 30),
        )

        val summary: ApiStatusSummary = ApiStatusService(store(logs)).summarize()

        val kakao: ProviderStatus = summary.providers.first { it.provider == "KAKAO" }
        assertThat(kakao.hourlyCalls).hasSize(24)
        assertThat(kakao.hourlyCalls.last().count).isEqualTo(2)
        assertThat(kakao.hourlyCalls.dropLast(1).sumOf { it.count }).isEqualTo(0)
        assertThat(summary.providers.first { it.provider == "TMAP" }.hourlyCalls.last().count).isEqualTo(1)
        assertThat(summary.hourlyTrend.last().count).isEqualTo(3)
    }

    @Test
    fun `쿼터 있는 DATAGOKR 만 소진 예측을 내고 예측총량은 현재속도의 24시간 외삽이다`() {
        val today: List<ApiCallLog> = (1..10).map {
            ApiCallLog(atEpochMs = todayStart + 1000, provider = "DATAGOKR", endpoint = "cctv", success = true, durationMs = 10)
        }

        val summary: ApiStatusSummary = ApiStatusService(store(today)).summarize()

        val datagokr: ProviderStatus = summary.providers.first { it.provider == "DATAGOKR" }
        assertThat(datagokr.todayCount).isEqualTo(10)
        val burn: Double = requireNotNull(datagokr.quotaBurnPerHour)
        assertThat(datagokr.quotaProjectedTodayTotal).isEqualTo((burn * 24).roundToInt())
        assertThat(datagokr.quotaProjectedTodayTotal).isGreaterThanOrEqualTo(10)

        val kakao: ProviderStatus = summary.providers.first { it.provider == "KAKAO" }
        assertThat(kakao.quotaBurnPerHour).isNull()
        assertThat(kakao.quotaProjectedTodayTotal).isNull()
    }

    @Test
    fun `오늘 호출이 0건이면 소진율과 예측총량은 0 이다`() {
        val yesterday: List<ApiCallLog> = (1..5).map {
            ApiCallLog(atEpochMs = todayStart - 1000, provider = "DATAGOKR", endpoint = "cctv", success = true, durationMs = 10)
        }

        val datagokr = ApiStatusService(store(yesterday)).summarize().providers.first { it.provider == "DATAGOKR" }

        assertThat(datagokr.todayCount).isEqualTo(0)
        assertThat(datagokr.quotaBurnPerHour).isEqualTo(0.0)
        assertThat(datagokr.quotaProjectedTodayTotal).isEqualTo(0)
    }

    @Test
    fun `빈 로그면 원천 전체가 total 0 시각 null 쿼터 경고 없음`() {
        val summary: ApiStatusSummary = ApiStatusService(store(emptyList())).summarize()

        assertThat(summary.windowSize).isEqualTo(0)
        assertThat(summary.providers.map { it.provider })
            .containsExactly("KAKAO", "TMAP", "DATAGOKR", "SAFEMAP", "SAFETYDATA")
        summary.providers.forEach { p ->
            assertThat(p.total).isEqualTo(0)
            assertThat(p.successRate).isNull()
            assertThat(p.avgDurationMs).isEqualTo(0L)
            assertThat(p.lastSuccessAtMs).isNull()
            assertThat(p.lastFailureAtMs).isNull()
            assertThat(p.quotaWarning).isFalse()
        }
        assertThat(summary.providers.first { it.provider == "DATAGOKR" }.quotaLimit).isEqualTo(1000)
        assertThat(summary.providers.first { it.provider == "KAKAO" }.quotaLimit).isNull()
    }

    @Test
    fun `빈 로그면 시간대 추이는 24버킷 모두 0 오래된에서 최신순`() {
        val trend: List<HourlyBucket> = ApiStatusService(store(emptyList())).summarize().hourlyTrend

        assertThat(trend).hasSize(24)
        assertThat(trend.map { it.count }).allMatch { it == 0 }
        val starts = trend.map { it.hourStartMs }
        assertThat(starts).isSorted()
        assertThat(starts.last() - starts.first()).isEqualTo(23L * 3_600_000L)
    }

    @Test
    fun `방금 호출은 원천 무관 마지막 버킷에 집계된다`() {
        val logs: List<ApiCallLog> = listOf(
            ApiCallLog(atEpochMs = seoulNow, provider = "KAKAO", endpoint = "search", success = true, durationMs = 10),
            ApiCallLog(atEpochMs = seoulNow, provider = "TMAP", endpoint = "route", success = true, durationMs = 20),
        )

        val trend: List<HourlyBucket> = ApiStatusService(store(logs)).summarize().hourlyTrend

        assertThat(trend.last().count).isEqualTo(2)
        assertThat(trend.dropLast(1).sumOf { it.count }).isEqualTo(0)
    }
}
