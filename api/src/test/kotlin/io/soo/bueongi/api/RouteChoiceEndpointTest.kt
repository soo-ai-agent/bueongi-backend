package io.soo.bueongi.api

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.stats.api.controller.RouteChoiceController
import io.soo.bueongi.core.route.stats.domain.service.RouteChoiceService
import io.soo.bueongi.storage.db.core.routestats.RouteChoiceLog
import io.soo.bueongi.storage.db.core.routestats.RouteChoiceStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@WebMvcTest(RouteChoiceController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RouteChoiceEndpointTest.FakeChoiceStoreConfig::class)
class RouteChoiceEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val store: RouteChoiceStore,
) {

    class RecordingChoiceStore : RouteChoiceStore {
        val recorded: MutableList<RouteChoiceLog> = mutableListOf()
        override fun record(log: RouteChoiceLog) {
            recorded.add(log)
        }
        override fun findRecent(limit: Int): List<RouteChoiceLog> = recorded.take(limit)
    }

    @TestConfiguration
    class FakeChoiceStoreConfig {
        @Bean
        fun routeChoiceStore(): RouteChoiceStore = RecordingChoiceStore()

        @Bean
        fun routeChoiceService(choiceStore: RouteChoiceStore): RouteChoiceService =
            RouteChoiceService(choiceStore, Clock.fixed(FIXED_NOW, ZoneOffset.UTC))

        companion object {
            val FIXED_NOW: Instant = Instant.parse("2026-08-17T21:30:00Z")
        }
    }

    private val validBody: String = """
        {"chosenType":"safe","safetyPreference":"safest","candidateCount":3,
         "distanceMeters":1240,"durationSeconds":930,"score":82,"destinationName":"강남역"}
    """.trimIndent()

    private fun recorded(): List<RouteChoiceLog> = (store as RecordingChoiceStore).recorded

    /** 스프링이 테스트 메서드 사이에 컨텍스트를 재사용해서 기록이 누적된다 — 매번 비운다. */
    @BeforeEach
    fun clearRecords() {
        (store as RecordingChoiceStore).recorded.clear()
    }

    @Test
    fun `선택을 보내면 202 로 접수하고 그대로 기록한다`() {
        mockMvc.post("/api/route-choices") {
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }.andExpect {
            status { isAccepted() }
        }

        assertThat(recorded()).hasSize(1)
        val log: RouteChoiceLog = recorded().first()
        assertThat(log.chosenType).isEqualTo(RouteType.SAFE)
        assertThat(log.preference).isEqualTo(SafetyPreference.SAFEST)
        assertThat(log.candidateCount).isEqualTo(3)
        assertThat(log.distanceMeters).isEqualTo(1240)
        assertThat(log.durationSeconds).isEqualTo(930)
        assertThat(log.score).isEqualTo(82)
        assertThat(log.destinationName).isEqualTo("강남역")
        assertThat(log.atEpochMs).isEqualTo(FakeChoiceStoreConfig.FIXED_NOW.toEpochMilli())
    }

    /**
     * 숫자 칸이 non-null 이면 Jackson 이 0 을 채워 "0m 를 골랐다"가 조용히 기록된다.
     * 어느 칸이 빠졌는지 짚어 400 이 나가야 한다.
     */
    @Test
    fun `거리가 빠지면 그 필드를 짚어 400`() {
        mockMvc.post("/api/route-choices") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"chosenType":"safe","safetyPreference":"safe","candidateCount":3,"durationSeconds":930}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("distanceMeters") }
        }

        assertThat(recorded()).isEmpty()
    }

    @Test
    fun `모르는 경로 종류는 400 이고 기록하지 않는다`() {
        mockMvc.post("/api/route-choices") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"chosenType":"scenic","safetyPreference":"safe","candidateCount":3,
                 "distanceMeters":1240,"durationSeconds":930}
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
        }

        assertThat(recorded()).isEmpty()
    }

    @Test
    fun `점수 없는 폴백 경로도 접수한다`() {
        mockMvc.post("/api/route-choices") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"chosenType":"main","safetyPreference":"balanced","candidateCount":3,
                 "distanceMeters":900,"durationSeconds":700}
            """.trimIndent()
        }.andExpect {
            status { isAccepted() }
        }

        assertThat(recorded().first().score).isNull()
        assertThat(recorded().first().destinationName).isNull()
    }

    @Test
    fun `관리자 조회는 종류별 3종과 창 상한을 낸다`() {
        mockMvc.post("/api/route-choices") {
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }

        mockMvc.get("/api/admin/route-choices")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(1) }
                jsonPath("$.windowCap") { value(2000) }
                jsonPath("$.byType.length()") { value(3) }
                jsonPath("$.byType[0].type") { value("safe") }
                jsonPath("$.byType[0].count") { value(1) }
                jsonPath("$.byType[1].type") { value("main") }
                jsonPath("$.byType[2].type") { value("fast") }
                jsonPath("$.byPreference.length()") { value(3) }
                jsonPath("$.byPreference[2].preference") { value("safest") }
                jsonPath("$.byPreference[2].count") { value(1) }
            }
    }
}
