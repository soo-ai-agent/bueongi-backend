package io.soo.bueongi.api

import io.soo.bueongi.core.safetyspot.api.controller.SafetySpotStatusController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(SafetySpotStatusController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminSafetySpotStatusEndpointTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `safetySpot-status 는 종류별 적재건수 갱신결과 CDN신선도를 반환한다`() {
        mockMvc.get("/api/admin/safety-spot-status")
            .andExpect {
                status { isOk() }
                // 시설 종류 수 — 가로등과 보안등을 나눠 7종이다.
                jsonPath("$.safetySpots.length()") { value(7) }
                jsonPath("$.safetySpots[0].type") { value("cctv") }
                jsonPath("$.safetySpots[0].loadedCount") { value(2) }
                jsonPath("$.safetySpots[0].lastRefreshStatus") { value("replaced") }
                jsonPath("$.safetySpots[0].lastRefreshCount") { value(2) }
                jsonPath("$.safetySpots[0].cdnVersion") { value("2026-06-19") }
            }
    }
}
