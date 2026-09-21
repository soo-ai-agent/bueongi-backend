package io.soo.bueongi.api

import io.soo.bueongi.core.route.stats.api.controller.RouteStatsController
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(RouteStatsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
@Tag("context")
class AdminRouteStatsEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `route-stats 는 엔드포인트 2종과 창 크기를 반환한다`() {
        mockMvc.get("/api/admin/route-stats")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(1) }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.byEndpoint.length()") { value(2) }
                jsonPath("$.byEndpoint[0].endpoint") { value("compare") }
                jsonPath("$.byEndpoint[0].count") { value(1) }
                jsonPath("$.byEndpoint[1].endpoint") { value("safe-compare") }
                jsonPath("$.byPreference.length()") { value(3) }
                jsonPath("$.byPreference[0].preference") { value("balanced") }
                jsonPath("$.byPreference[1].preference") { value("safe") }
                jsonPath("$.byPreference[2].preference") { value("safest") }
                jsonPath("$.topDestinations[0].name") { value("강남역") }
                jsonPath("$.hourlyTrend.length()") { value(24) }
            }
    }
}
