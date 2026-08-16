package io.soo.bueongi.api

import io.soo.bueongi.core.ads.api.controller.AdStatsController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(AdStatsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminAdStatsEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `ad-stats 는 총계 오늘 지면별 카운트 시간대 트렌드를 반환한다`() {
        mockMvc.get("/api/admin/ad-stats")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(2) }
                jsonPath("$.today") { isNumber() }
                jsonPath("$.byPlacement.interstitial") { value(1) }
                jsonPath("$.byPlacement.native") { value(1) }
                jsonPath("$.hourlyTrend.length()") { value(24) }
            }
    }
}
