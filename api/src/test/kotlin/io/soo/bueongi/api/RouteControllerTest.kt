package io.soo.bueongi.api

import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteFacilityService
import io.soo.bueongi.core.route.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.core.route.api.controller.RouteController
import io.soo.bueongi.client.tmap.TmapRouteClient

import io.soo.bueongi.storage.db.core.facility.store.FacilityRepository
import io.soo.bueongi.storage.db.core.facility.store.FacilityUploadStore
import io.soo.bueongi.core.route.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.route.safety.service.SafetyScorer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(RouteController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    RouteService::class,
    RouteFacilityService::class,
    SafeRouteService::class,
    RouteCandidateCollector::class,
    RouteOptionAssembler::class,
    RouteMarkerBuilder::class,
    RouteCandidateMerger::class,
    SafetyScorer::class,
    SafeDetourPlanner::class,
    FacilityRepository::class,
    FacilityUploadStore::class,
)
class RouteControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockBean
    private lateinit var tmapRouteClient: TmapRouteClient

    private val validBody: String = """
        {"origin":{"lat":37.4979,"lng":127.0276},
         "destination":{"lat":37.5572,"lng":126.9245,"name":"홍대입구역"}}
    """.trimIndent()

    @Test
    fun `compare 정상 요청은 200 과 safe main fast 3개 경로`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("application/json") }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].type") { value("safe") }
            jsonPath("$[1].type") { value("main") }
            jsonPath("$[2].type") { value("fast") }
            jsonPath("$[0].id") { exists() }
            jsonPath("$[0].name") { exists() }
            jsonPath("$[0].time") { exists() }
            jsonPath("$[0].dist") { exists() }
            jsonPath("$[0].tags[0].text") { exists() }
            jsonPath("$[0].tags[0].variant") { exists() }
        }
    }

    @Test
    fun `safe-compare 는 Tmap 키 없으면 폴백으로 200 과 safe main fast 3개 경로`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].type") { value("safe") }
            jsonPath("$[2].type") { value("fast") }
        }
    }

    @Test
    fun `safe-compare origin 누락이면 422 ORIGIN_REQUIRED`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245}}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `facilities 정상 요청은 200 과 pois summary`() {
        val body: String = validBody.dropLast(1) + ""","routeType":"safe"}"""
        mockMvc.post("/api/routes/facilities") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.pois") { isArray() }
            jsonPath("$.summary") { exists() }
            // 편의점 수의 와이어 키는 store 다(프론트 safetyFacilities.ts 계약) — 프로퍼티명은 convenienceStore.
            jsonPath("$.summary.store") { exists() }
            jsonPath("$.summary.convenienceStore") { doesNotExist() }
        }
    }

    @Test
    fun `origin 누락이면 422 ORIGIN_REQUIRED`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245}}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `출발지와 도착지가 같은 좌표면 422`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":127.0}}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `facilities 도 원거리 상한을 거른다(422 TRIP_TOO_FAR)`() {
        mockMvc.post("/api/routes/facilities") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.0,"lng":127.0},"destination":{"lat":38.5,"lng":127.0},"routeType":"safe"}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `destination 좌표 누락이면 400`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.4979,"lng":127.0276},"destination":{"name":"x"}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `위도 범위 초과(lat 100)면 400`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":100.0,"lng":127.0276},"destination":{"lat":37.5,"lng":126.9}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `경도 범위 초과(lng 200)면 400`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":200.0}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `좌표가 양의 무한대로 오버플로(lat 1e400)면 400 VALIDATION_FAILED`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":1e400,"lng":127.0},"destination":{"lat":37.5,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("origin.lat") }
        }
    }

    @Test
    fun `좌표가 음의 무한대로 오버플로(lng -1e400)면 400 VALIDATION_FAILED`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":-1e400},"destination":{"lat":37.6,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("origin.lng") }
        }
    }

    @Test
    fun `비숫자 리터럴 NaN 좌표는 400 MALFORMED_REQUEST`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":NaN,"lng":127.0},"destination":{"lat":37.6,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
        }
    }

    @Test
    fun `비숫자 리터럴 Infinity 좌표는 400 MALFORMED_REQUEST`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":Infinity,"lng":127.0},"destination":{"lat":37.6,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
        }
    }
}
