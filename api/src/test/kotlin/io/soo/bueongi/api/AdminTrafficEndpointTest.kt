package io.soo.bueongi.api

import io.soo.bueongi.core.ops.api.controller.TrafficController
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
    @param:Autowired private val mockMvc: MockMvc,
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

    /** 사용자 표와 관리자 표가 한 응답 안에서 갈려 있어야 화면이 섞어 그리지 않는다. */
    @Test
    fun `traffic 은 관리자 호출을 사용자 집계와 갈라서 반환한다`() {
        mockMvc.get("/api/admin/traffic")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalCalls") { value(1) }
                jsonPath("$.byEndpoint[0].endpoint") { value("/api/places/search") }
                jsonPath("$.adminCalls.totalCalls") { value(1) }
                jsonPath("$.adminCalls.callsToday") { value(1) }
                jsonPath("$.adminCalls.windowCap") { value(1000) }
                jsonPath("$.adminCalls.byEndpoint[0].endpoint") { value("/api/admin/traffic") }
                jsonPath("$.adminCalls.hourlyCalls.length()") { value(24) }
                jsonPath("$.adminCalls.estimatedUsers") { doesNotExist() }
            }
    }
}
