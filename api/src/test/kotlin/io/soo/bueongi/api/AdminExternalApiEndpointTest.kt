package io.soo.bueongi.api

import io.soo.bueongi.core.ops.api.controller.ExternalApiController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ExternalApiController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminExternalApiEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `external-calls 는 원천별 호출 수 실패 지연을 반환한다`() {
        mockMvc.get("/api/admin/external-calls")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.totalCalls") { value(2) }
                jsonPath("$.failureCount") { value(1) }
                jsonPath("$.callsToday") { value(2) }
                jsonPath("$.hourlyCalls.length()") { value(24) }
                jsonPath("$.byProvider.length()") { value(2) }
                jsonPath("$.byProvider[0].provider") { value("tmap") }
                jsonPath("$.byProvider[0].totalCalls") { value(1) }
                jsonPath("$.byProvider[0].successRate") { value(1.0) }
                jsonPath("$.byProvider[0].avgDurationMs") { value(320) }
                jsonPath("$.byProvider[0].topEndpoints[0].endpoint") { value("/tmap/routes/pedestrian") }
            }
    }

    /** 쿼터 소진(429)은 실패로 세고 사유를 남겨야 관리자가 원인을 바로 안다. */
    @Test
    fun `쿼터 소진은 실패로 세고 마지막 사유를 남긴다`() {
        mockMvc.get("/api/admin/external-calls")
            .andExpect {
                status { isOk() }
                jsonPath("$.byProvider[1].provider") { value("kakao") }
                jsonPath("$.byProvider[1].failureCount") { value(1) }
                jsonPath("$.byProvider[1].successRate") { value(0.0) }
                jsonPath("$.byProvider[1].lastFailureReason") { value("HTTP 429") }
            }
    }
}
