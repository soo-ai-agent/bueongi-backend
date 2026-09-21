package io.soo.bueongi.core.route.stats.domain

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.stats.domain.model.RouteChoice
import io.soo.bueongi.core.route.stats.domain.service.RouteChoiceService
import io.soo.bueongi.storage.db.core.routestats.ROUTE_CHOICE_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteChoiceLog
import io.soo.bueongi.storage.db.core.routestats.RouteChoiceStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RouteChoiceServiceTest {
    private class RecordingStore(private val preset: List<RouteChoiceLog> = emptyList()) : RouteChoiceStore {
        val recorded: MutableList<RouteChoiceLog> = mutableListOf()

        override fun record(log: RouteChoiceLog) {
            recorded.add(log)
        }

        override fun findRecent(limit: Int): List<RouteChoiceLog> = preset.take(limit)
    }

    private val fixedNow: Instant = Instant.parse("2026-08-17T21:30:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private fun choice(
        type: RouteType = RouteType.SAFE,
        preference: SafetyPreference = SafetyPreference.SAFE,
    ) = RouteChoice(
        chosenType = type,
        candidateCount = 3,
        score = 82,
        distanceMeters = 1200,
        durationSeconds = 900,
        preference = preference,
        destinationName = "강남역",
    )

    private fun log(
        type: RouteType = RouteType.SAFE,
        preference: SafetyPreference = SafetyPreference.SAFE,
    ) = RouteChoiceLog(
        atEpochMs = fixedNow.toEpochMilli(),
        chosenType = type,
        candidateCount = 3,
        score = 82,
        distanceMeters = 1200,
        durationSeconds = 900,
        preference = preference,
        destinationName = "강남역",
    )

    @Test
    fun `record 는 시각을 붙여 전 칸을 그대로 저장한다`() {
        val store = RecordingStore()

        RouteChoiceService(store, clock).record(choice(type = RouteType.FAST))

        assertThat(store.recorded).hasSize(1)
        val saved: RouteChoiceLog = store.recorded.first()
        assertThat(saved.atEpochMs).isEqualTo(fixedNow.toEpochMilli())
        assertThat(saved.chosenType).isEqualTo(RouteType.FAST)
        assertThat(saved.candidateCount).isEqualTo(3)
        assertThat(saved.score).isEqualTo(82)
        assertThat(saved.distanceMeters).isEqualTo(1200)
        assertThat(saved.durationSeconds).isEqualTo(900)
        assertThat(saved.preference).isEqualTo(SafetyPreference.SAFE)
        assertThat(saved.destinationName).isEqualTo("강남역")
    }

    @Test
    fun `점수 없는 폴백 경로를 골라도 기록된다`() {
        val store = RecordingStore()

        RouteChoiceService(store, clock).record(choice().copy(score = null))

        assertThat(store.recorded.first().score).isNull()
    }

    @Test
    fun `byType 은 경로 종류 3종 고정이고 없는 종류는 0 이다`() {
        val logs = List(3) { log(type = RouteType.SAFE) } + List(1) { log(type = RouteType.MAIN) }

        val summary = RouteChoiceService(RecordingStore(logs), clock).summarize()

        assertThat(summary.total).isEqualTo(4)
        assertThat(summary.windowCap).isEqualTo(ROUTE_CHOICE_CAP)
        assertThat(summary.byType.map { it.type })
            .containsExactly(RouteType.SAFE, RouteType.MAIN, RouteType.FAST)
        assertThat(summary.byType.map { it.count }).containsExactly(3, 1, 0)
    }

    @Test
    fun `byPreference 는 안심강도 3종 고정이고 없는 값은 0 이다`() {
        val logs =
            List(2) { log(preference = SafetyPreference.SAFEST) } +
                List(1) { log(preference = SafetyPreference.BALANCED) }

        val byPreference = RouteChoiceService(RecordingStore(logs), clock).summarize().byPreference

        assertThat(byPreference.map { it.preference })
            .containsExactly(SafetyPreference.BALANCED, SafetyPreference.SAFE, SafetyPreference.SAFEST)
        assertThat(byPreference.map { it.count }).containsExactly(1, 0, 2)
    }

    @Test
    fun `기록이 없으면 total 0 이고 종류별 자리는 그대로 남는다`() {
        val summary = RouteChoiceService(RecordingStore(), clock).summarize()

        assertThat(summary.total).isEqualTo(0)
        assertThat(summary.byType).hasSize(RouteType.entries.size)
        assertThat(summary.byType.map { it.count }).containsOnly(0)
        assertThat(summary.byPreference).hasSize(SafetyPreference.entries.size)
        assertThat(summary.byPreference.map { it.count }).containsOnly(0)
    }
}
