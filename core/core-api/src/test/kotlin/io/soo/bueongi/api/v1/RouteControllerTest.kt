package io.soo.bueongi.api.v1
import io.soo.bueongi.api.v1.request.requireDistinctEndpoints
import io.soo.bueongi.api.v1.request.requireWithinReach
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.domain.route.RouteFacilityService
import io.soo.bueongi.domain.route.RouteOption
import io.soo.bueongi.domain.route.RouteService
import io.soo.bueongi.domain.route.SafeRouteService
import io.soo.bueongi.domain.route.RouteCandidateMerger
import io.soo.bueongi.domain.route.RouteMarkerBuilder

import io.soo.bueongi.storage.db.core.facility.FacilityRepository
import io.soo.bueongi.storage.db.core.facility.FacilityUploadStore
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyScorer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * [RouteController] 슬라이스 테스트(@WebMvcTest). 실 서비스([RouteService]/[RouteFacilityService]/[SafeRouteService]) 를
 * import 해 compare/facilities/safe-compare 의 HTTP 계약을 고정한다:
 *  - 정상 요청 → 200 + 프론트 계약 JSON shape
 *  - destination null → 400 / origin 누락 → 422 ORIGIN_REQUIRED(복구 가능 상태로 구분)
 *  - 위경도 범위 초과(lat>90 / lng>180) → 400
 *
 * safe-compare 는 Tmap 키 미설정(테스트 환경)이라 결정론적 폴백(compare)로 동작한다 — 키 의존 외부 호출 없이
 * 폴백 계약(safe/main/fast)을 고정한다. (실 Tmap/점수/우회는 [SafeRouteServiceTest] 등에서 목으로 검증.)
 */
@WebMvcTest(RouteController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    RouteService::class,
    RouteFacilityService::class,
    SafeRouteService::class,
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
