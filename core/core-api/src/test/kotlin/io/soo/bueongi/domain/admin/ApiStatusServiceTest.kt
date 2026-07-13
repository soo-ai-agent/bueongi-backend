package io.soo.bueongi.domain.admin

import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * [ApiStatusService] 집계 정확성 — 저장된 로그만으로 성공률·평균 소요·최근 성공/실패·오늘 쿼터를 계산한다.
 * 저장소는 넘겨준 로그를 그대로 돌려주는 페이크(가장 얇은 대역)로 대체한다.
 */
class ApiStatusServiceTest {

    private fun store(logs: List<ApiCallLog>): ApiCallLogStore = object : ApiCallLogStore {
        override fun record(log: ApiCallLog) = Unit
        override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> = logs.take(limit)
    }

    private val seoulNow = System.currentTimeMillis()
    private val todayStart = java.time.Instant.ofEpochMilli(seoulNow)
        .atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
        .atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()

    @Test
    fun `성공 실패 섞인 로그로 성공률 평균 최근시각을 집계한다`() {
        val logs = listOf(
            ApiCallLog(atEpochMs = 300, provider = "KAKAO", endpoint = "search", success = true, durationMs = 100),
            ApiCallLog(atEpochMs = 200, provider = "KAKAO", endpoint = "search", success = false, durationMs = 200, detail = "boom"),
            ApiCallLog(atEpochMs = 100, provider = "KAKAO", endpoint = "search", success = true, durationMs = 300),
        )

        val kakao = ApiStatusService(store(logs)).summarize().providers.first { it.provider == "KAKAO" }

        assertThat(kakao.total).isEqualTo(3)
        assertThat(kakao.successCount).isEqualTo(2)
        assertThat(kakao.successRate).isEqualTo(2.0 / 3.0)
        assertThat(kakao.avgDurationMs).isEqualTo(200L)
        assertThat(kakao.lastSuccessAtMs).isEqualTo(300L)
        assertThat(kakao.lastFailureAtMs).isEqualTo(200L)
    }

    @Test
    fun `DATAGOKR 오늘 800건 이상이면 쿼터 경고 어제 것은 제외`() {
        val today = (1..820).map {
            ApiCallLog(atEpochMs = todayStart + 1000, provider = "DATAGOKR", endpoint = "cctv", success = true, durationMs = 10)
        }
        val yesterday = (1..50).map {
            ApiCallLog(atEpochMs = todayStart - 1000, provider = "DATAGOKR", endpoint = "cctv", success = true, durationMs = 10)
        }

        val datagokr = ApiStatusService(store(today + yesterday)).summarize().providers.first { it.provider == "DATAGOKR" }

        assertThat(datagokr.todayCount).isEqualTo(820)
        assertThat(datagokr.quotaLimit).isEqualTo(1000)
        assertThat(datagokr.quotaWarning).isTrue()
    }

    @Test
    fun `빈 로그면 4종 모두 total 0 시각 null 쿼터 경고 없음`() {
        val summary = ApiStatusService(store(emptyList())).summarize()

        assertThat(summary.windowSize).isEqualTo(0)
        assertThat(summary.providers.map { it.provider })
            .containsExactly("KAKAO", "TMAP", "DATAGOKR", "SAFEMAP")
        summary.providers.forEach { p ->
            assertThat(p.total).isEqualTo(0)
            assertThat(p.successRate).isEqualTo(0.0)
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
        val trend = ApiStatusService(store(emptyList())).summarize().hourlyTrend

        assertThat(trend).hasSize(24)
        assertThat(trend.map { it.count }).allMatch { it == 0 }
        // 버킷 시작 시각이 오래된→최신 순으로 1시간(3_600_000ms)씩 증가한다.
        val starts = trend.map { it.hourStartMs }
        assertThat(starts).isSorted()
        assertThat(starts.last() - starts.first()).isEqualTo(23L * 3_600_000L)
    }

    @Test
    fun `방금 호출은 원천 무관 마지막 버킷에 집계된다`() {
        val logs = listOf(
            ApiCallLog(atEpochMs = seoulNow, provider = "KAKAO", endpoint = "search", success = true, durationMs = 10),
            ApiCallLog(atEpochMs = seoulNow, provider = "TMAP", endpoint = "route", success = true, durationMs = 20),
        )

        val trend = ApiStatusService(store(logs)).summarize().hourlyTrend

        // 현재 시각 정각 버킷(마지막)에 2건(원천 합산), 나머지 23버킷은 0.
        assertThat(trend.last().count).isEqualTo(2)
        assertThat(trend.dropLast(1).sumOf { it.count }).isEqualTo(0)
    }
}
