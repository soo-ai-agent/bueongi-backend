package io.soo.bueongi.api

import io.soo.bueongi.core.safetyspot.api.controller.SafetySpotStatusController
import org.hamcrest.Matchers.containsString
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
                // 지도가 이 지역을 가리킬 한 점 — 지점 두 개(37.500·37.501)의 평균.
                jsonPath("$[0].center.lat") { value(37.5005) }
                jsonPath("$[0].center.lng") { value(127.0) }
            }
    }

    @Test
    fun `regions spots 는 그 지역 지점을 와이어 종류값으로 내고 전체 건수를 함께 말한다`() {
        mockMvc.get("/api/admin/safety-spot-status/regions/3130000/spots")
            .andExpect {
                status { isOk() }
                jsonPath("$.code") { value("3130000") }
                jsonPath("$.name") { value("마포구") }
                jsonPath("$.sido") { value("서울특별시") }
                jsonPath("$.total") { value(2) }
                jsonPath("$.returned") { value(2) }
                jsonPath("$.spots[0].type") { value("cctv") }
                jsonPath("$.spots[0].lat") { value(37.5) }
                jsonPath("$.spots[0].lng") { value(127.0) }
                // 원천이 이름을 안 주는 시설도 키는 그대로 나가야 프론트가 자리를 비울 수 있다.
                content { string(containsString("\"name\":null")) }
            }
    }

    @Test
    fun `regions spots 는 잘린 사실을 returned 로 밝힌다`() {
        mockMvc.get("/api/admin/safety-spot-status/regions/3130000/spots?limit=1")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(2) }
                jsonPath("$.returned") { value(1) }
                jsonPath("$.spots.length()") { value(1) }
            }
    }

    @Test
    fun `regions spots 는 표시 개수가 0 이하면 400 으로 거부한다`() {
        mockMvc.get("/api/admin/safety-spot-status/regions/3130000/spots?limit=0")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_SPOT_LIMIT") }
                jsonPath("$.errors[0].field") { value("limit") }
            }
    }

    @Test
    fun `regions spots 는 없는 자치단체도 빈 목록으로 답한다`() {
        mockMvc.get("/api/admin/safety-spot-status/regions/9999999/spots")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(0) }
                jsonPath("$.returned") { value(0) }
                jsonPath("$.spots.length()") { value(0) }
            }
    }
}
