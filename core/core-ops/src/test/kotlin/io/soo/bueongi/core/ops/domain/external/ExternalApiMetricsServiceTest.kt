package io.soo.bueongi.core.ops.domain.external

import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.core.ops.domain.external.model.ExternalApiSummary
import io.soo.bueongi.core.ops.domain.external.model.ExternalApiUsage
import io.soo.bueongi.core.ops.domain.external.service.ExternalApiMetricsService
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetric
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ExternalApiMetricsServiceTest {

    // 2026-08-22 12:00 KST — 같은 날 03:00 KST 는 오늘이고, 전날 23:00 KST 는 어제다.
    private val nowMs: Long = Instant.parse("2026-08-22T03:00:00Z").toEpochMilli()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(nowMs), ZoneOffset.UTC)

    private fun storeOf(vararg metrics: ExternalCallMetric): ExternalCallMetricStore =
        object : ExternalCallMetricStore {
            override fun record(metric: ExternalCallMetric) = Unit
            override fun findRecent(limit: Int): List<ExternalCallMetric> = metrics.toList()
        }

    private fun call(
        provider: ExternalApiProvider,
        status: Int = 200,
        durationMs: Long = 100,
        atEpochMs: Long = nowMs,
        endpoint: String = "/x",
        failureReason: String? = null,
    ): ExternalCallMetric = ExternalCallMetric(
        atEpochMs = atEpochMs, provider = provider, method = "GET", endpoint = endpoint,
        status = status, durationMs = durationMs, failureReason = failureReason,
    )

    @Test
    fun `원천별로 호출 수와 성공률을 낸다`() {
        val service = ExternalApiMetricsService(
            storeOf(
                call(ExternalApiProvider.TMAP),
                call(ExternalApiProvider.TMAP, status = 500),
                call(ExternalApiProvider.KAKAO),
            ),
            clock,
        )

        val summary: ExternalApiSummary = service.summarize()

        assertThat(summary.totalCalls).isEqualTo(3)
        assertThat(summary.failureCount).isEqualTo(1)
        val tmap: ExternalApiUsage = summary.byProvider.first { it.provider == ExternalApiProvider.TMAP }
        assertThat(tmap.totalCalls).isEqualTo(2)
        assertThat(tmap.failureCount).isEqualTo(1)
        assertThat(tmap.successRate).isEqualTo(0.5)
    }

    @Test
    fun `많이 부른 원천이 앞에 온다`() {
        val service = ExternalApiMetricsService(
            storeOf(
                call(ExternalApiProvider.KAKAO),
                call(ExternalApiProvider.TMAP),
                call(ExternalApiProvider.TMAP),
                call(ExternalApiProvider.TMAP),
            ),
            clock,
        )

        assertThat(service.summarize().byProvider.map { it.provider })
            .containsExactly(ExternalApiProvider.TMAP, ExternalApiProvider.KAKAO)
    }

    /** 일일 한도가 있는 원천을 볼 때 "오늘 몇 번"이 판단 근거다 — 어제 것이 섞이면 안 된다. */
    @Test
    fun `오늘 호출 수는 서비스 시간대의 자정부터 센다`() {
        val yesterdayMs: Long = Instant.parse("2026-08-21T13:00:00Z").toEpochMilli()
        val service = ExternalApiMetricsService(
            storeOf(
                call(ExternalApiProvider.SAFE182),
                call(ExternalApiProvider.SAFE182, atEpochMs = yesterdayMs),
            ),
            clock,
        )

        val summary: ExternalApiSummary = service.summarize()
        assertThat(summary.totalCalls).isEqualTo(2)
        assertThat(summary.callsToday).isEqualTo(1)
    }

    @Test
    fun `최근 한 시간 호출 수는 한 시간 밖을 빼고 센다`() {
        val twoHoursAgoMs: Long = nowMs - 2 * 60 * 60 * 1000L
        val service = ExternalApiMetricsService(
            storeOf(
                call(ExternalApiProvider.TMAP),
                call(ExternalApiProvider.TMAP, atEpochMs = twoHoursAgoMs),
            ),
            clock,
        )

        assertThat(service.summarize().callsLastHour).isEqualTo(1)
    }

    @Test
    fun `마지막 실패 사유를 남긴다 - 연결 실패면 예외 종류 응답이 왔으면 상태 코드`() {
        val earlierMs: Long = nowMs - 60_000
        val service = ExternalApiMetricsService(
            storeOf(
                call(ExternalApiProvider.KAKAO, status = 0, failureReason = "IOException", atEpochMs = earlierMs),
                call(ExternalApiProvider.KAKAO, status = 429),
                call(ExternalApiProvider.TMAP, status = 0, failureReason = "IOException"),
            ),
            clock,
        )

        val summary: ExternalApiSummary = service.summarize()
        assertThat(summary.byProvider.first { it.provider == ExternalApiProvider.KAKAO }.lastFailureReason)
            .isEqualTo("HTTP 429")
        assertThat(summary.byProvider.first { it.provider == ExternalApiProvider.TMAP }.lastFailureReason)
            .isEqualTo("IOException")
    }

    @Test
    fun `실패가 없으면 사유는 비어 있다`() {
        val service = ExternalApiMetricsService(storeOf(call(ExternalApiProvider.TMAP)), clock)

        assertThat(service.summarize().byProvider.single().lastFailureReason).isNull()
    }

    @Test
    fun `많이 부른 경로를 원천 안에서 추린다`() {
        val service = ExternalApiMetricsService(
            storeOf(
                call(ExternalApiProvider.DATA_GO_KR, endpoint = "/a"),
                call(ExternalApiProvider.DATA_GO_KR, endpoint = "/a"),
                call(ExternalApiProvider.DATA_GO_KR, endpoint = "/b", status = 500),
            ),
            clock,
        )

        val endpoints = service.summarize().byProvider.single().topEndpoints
        assertThat(endpoints.first().endpoint).isEqualTo("/a")
        assertThat(endpoints.first().count).isEqualTo(2)
        assertThat(endpoints.last().failureCount).isEqualTo(1)
    }

    @Test
    fun `기록이 없으면 빈 요약을 낸다`() {
        val service = ExternalApiMetricsService(storeOf(), clock)

        val summary: ExternalApiSummary = service.summarize()
        assertThat(summary.totalCalls).isZero()
        assertThat(summary.byProvider).isEmpty()
        assertThat(summary.hourlyCalls).hasSize(24)
    }
}
