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
                // 지점 종류 수 — 안심시설 7종 + 우범지역 1종.
                jsonPath("$.safetySpots.length()") { value(8) }
                jsonPath("$.safetySpots[0].type") { value("cctv") }
                jsonPath("$.safetySpots[0].loadedCount") { value(2) }
                jsonPath("$.safetySpots[0].lastRefreshStatus") { value("replaced") }
                jsonPath("$.safetySpots[0].lastRefreshCount") { value(2) }
                jsonPath("$.safetySpots[0].cdnVersion") { value("2026-06-19") }
            }
    }

    @Test
    fun `regions 는 자치단체별 종류 건수와 이름을 반환한다`() {
        mockMvc.get("/api/admin/safety-spot-status/regions")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].code") { value("3130000") }
                jsonPath("$[0].sido") { value("서울특별시") }
                jsonPath("$[0].name") { value("마포구") }
                jsonPath("$[0].total") { value(2) }
                jsonPath("$[0].counts.cctv") { value(2) }
            }
    }
}
