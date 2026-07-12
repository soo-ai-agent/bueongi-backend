package io.soo.bueongi.api.v1

import io.soo.bueongi.domain.facility.FakeFacilitySource
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource

import io.soo.bueongi.api.ApiControllerAdvice
import io.soo.bueongi.domain.facility.NearestPoliceFinder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * [FacilityController] 슬라이스 테스트. 가짜 시설 소스로 거리순 정렬·반경 제한·검증(400)의
 * HTTP 계약을 고정한다 — 위급 기능 폴백 API라 계약이 흔들리면 프론트 긴급 시트가 조용히 깨진다.
 */
@WebMvcTest(FacilityController::class)
// 보안 필터 비활성(슬라이스는 컨트롤러 계약만 검증 — 실 보안은 AdminAuthGateTest).
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiControllerAdvice::class, NearestPoliceFinder::class, FacilityControllerTest.FakeSourceConfig::class)
class FacilityControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @TestConfiguration
    class FakeSourceConfig {
        @Bean
        fun facilitySpatialSource(): FacilitySpatialSource =
            FakeFacilitySource(
                mapOf(
                    FacilityType.POLICE to listOf(
                        // 강남역(37.4981, 127.0276) 기준: 멀리(약 1.5km) → 가까이(약 120m) 순서로 저장해
                        // 응답이 거리순으로 재정렬되는지 검증한다.
                        FacilityPoint(FacilityType.POLICE, 37.5117, 127.0276, name = "먼 지구대", phone = "02-000-0001"),
                        FacilityPoint(FacilityType.POLICE, 37.4992, 127.0276, name = "가까운 파출소", phone = "02-000-0002"),
                        // 반경(10km) 밖(약 45km) — 결과에서 제외되어야 한다.
                        FacilityPoint(FacilityType.POLICE, 37.9000, 127.0276, name = "반경 밖 파출소"),
                    ),
                ),
            )
    }

    @Test
    fun `거리순 정렬 + 반경 밖 제외 + distance_m 포함으로 응답한다`() {
        mockMvc.get("/api/facilities/police/nearest") {
            param("lat", "37.4981")
            param("lng", "127.0276")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].name") { value("가까운 파출소") }
            jsonPath("$.items[0].tel") { value("02-000-0002") }
            jsonPath("$.items[0].distance_m") { isNumber() }
            jsonPath("$.items[1].name") { value("먼 지구대") }
        }
    }

    @Test
    fun `limit 파라미터로 개수를 제한한다`() {
        mockMvc.get("/api/facilities/police/nearest") {
            param("lat", "37.4981")
            param("lng", "127.0276")
            param("limit", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].name") { value("가까운 파출소") }
        }
    }

    @Test
    fun `위경도 범위 초과는 400을 반환한다`() {
        mockMvc.get("/api/facilities/police/nearest") {
            param("lat", "123.0")
            param("lng", "127.0276")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
