package io.soo.bueongi.api

import io.soo.bueongi.core.admin.api.AdController
import io.soo.bueongi.api.ApiControllerAdvice
import io.soo.bueongi.core.admin.domain.adstats.AdStatsService
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
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
import org.springframework.test.web.servlet.post

@WebMvcTest(AdController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdStatsService::class, ApiControllerAdvice::class, AdControllerTest.CapturingStoreConfig::class)
class AdControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @BeforeEach
    fun clearRecorded() {
        CapturingStoreConfig.recorded.clear()
    }

    @Test
    fun `유효한 placement 로 노출을 기록하면 204 와 빈 본문 그리고 저장소에 1건 기록`() {
        mockMvc.post("/api/ads/impression") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"placement":"interstitial"}"""
        }.andExpect {
            status { isNoContent() }
            content { string("") }
        }

        assertThat(CapturingStoreConfig.recorded.map { it.placement }).containsExactly("interstitial")
    }

    @Test
    fun `placement 가 없으면 400 VALIDATION_FAILED 이고 기록하지 않는다`() {
        mockMvc.post("/api/ads/impression") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("placement") }
        }

        assertThat(CapturingStoreConfig.recorded).isEmpty()
    }

    @Test
    fun `허용값이 아닌 placement 는 400 VALIDATION_FAILED 이고 기록하지 않는다`() {
        mockMvc.post("/api/ads/impression") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"placement":"banner"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("placement") }
        }

        assertThat(CapturingStoreConfig.recorded).isEmpty()
    }

    @TestConfiguration
    class CapturingStoreConfig {
        @Bean
        fun adEventStore(): AdEventStore = object : AdEventStore {
            override fun record(event: AdEvent) {
                recorded += event
            }

            override fun findRecent(limit: Int): List<AdEvent> = recorded
        }

        @Bean
        fun adStatsService(store: AdEventStore): AdStatsService = AdStatsService(store)

        companion object {
            val recorded: MutableList<AdEvent> = mutableListOf<AdEvent>()
        }
    }
}
