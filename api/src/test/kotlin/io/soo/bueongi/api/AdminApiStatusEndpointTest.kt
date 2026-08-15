package io.soo.bueongi.api

import io.soo.bueongi.core.ops.api.ApiStatusController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ApiStatusController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminApiStatusEndpointTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `접속 내역을 200 과 배열로 반환한다`() {
        mockMvc.get("/api/admin/api-logs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$[0].provider") { value("KAKAO") }
                jsonPath("$[0].endpoint") { value("search") }
                jsonPath("$[0].success") { value(true) }
                jsonPath("$[0].durationMs") { isNumber() }
            }
    }
    @Test
    fun `limit 은 상한(500)으로 절삭되어 저장소에 전달된다`() {
        mockMvc.get("/api/admin/api-logs") { param("limit", "9999") }
            .andExpect { status { isOk() } }
        org.assertj.core.api.Assertions.assertThat(AdminEndpointFixtures.FakeStoreConfig.lastLimit).isEqualTo(500)
    }
    @Test
    fun `api-status 는 원천 전체와 windowSize 를 반환한다`() {
        mockMvc.get("/api/admin/api-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.providers.length()") { value(5) }
                jsonPath("$.providers[0].provider") { value("KAKAO") }
                jsonPath("$.providers[0].total") { value(1) }
                jsonPath("$.providers[0].successRate") { value(1.0) }
            }
    }
}
