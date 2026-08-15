package io.soo.bueongi.api

import io.soo.bueongi.core.ops.api.TrafficController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(TrafficController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminTrafficEndpointTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `traffic 은 엔드포인트 집계와 예측 사용자 시간대 일자 추이를 반환한다`() {
        mockMvc.get("/api/admin/traffic")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalCalls") { value(1) }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.byEndpoint[0].method") { value("GET") }
                jsonPath("$.byEndpoint[0].endpoint") { value("/api/places/search") }
                jsonPath("$.byEndpoint[0].count") { value(1) }
                jsonPath("$.byEndpoint[0].successRate") { value(1.0) }
                jsonPath("$.byEndpoint[0].distinctClients") { value(1) }
                jsonPath("$.estimatedUsers.today") { value(1) }
                jsonPath("$.estimatedUsers.callsWithoutClientId") { value(0) }
                jsonPath("$.hourlyCalls.length()") { value(24) }
                jsonPath("$.dailyActiveUsers.length()") { value(7) }
            }
    }
}
