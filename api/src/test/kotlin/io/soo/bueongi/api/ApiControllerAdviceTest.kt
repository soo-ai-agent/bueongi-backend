package io.soo.bueongi.api

import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.api.ApiControllerAdvice

import io.soo.bueongi.storage.db.core.facility.store.FacilityRepository
import io.soo.bueongi.storage.db.core.facility.store.FacilityUploadStore
import io.soo.bueongi.core.route.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.route.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.service.RouteFacilityService
import io.soo.bueongi.core.route.api.controller.RouteController
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.marker.service.RouteMarkerBuilder
import io.soo.bueongi.client.tmap.TmapRouteClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(RouteController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    RouteService::class,
    RouteFacilityService::class,
    ApiControllerAdvice::class,
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
class ApiExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockBean
    private lateinit var tmapRouteClient: TmapRouteClient

    @Test
    fun `위경도 범위 초과는 400 과 표준 errors 배열을 반환`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":100.0,"lng":127.0276},"destination":{"lat":37.5,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Validation Failed") }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.errors") { isArray() }
            jsonPath("$.errors[0].field") { value("origin.lat") }
            jsonPath("$.errors[0].message") { exists() }
            jsonPath("$.errors[0].rejectedValue") { value(100.0) }
        }
    }

    @Test
    fun `origin 누락은 422 와 ORIGIN_REQUIRED 코드를 반환`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245}}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.title") { value("Origin Required") }
            jsonPath("$.code") { value("ORIGIN_REQUIRED") }
            jsonPath("$.status") { value(422) }
        }
    }

    @Test
    fun `출발지와 도착지가 같은 좌표면 422 와 SAME_ORIGIN_DESTINATION 코드를 반환`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":127.0}}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.title") { value("Same Origin And Destination") }
            jsonPath("$.code") { value("SAME_ORIGIN_DESTINATION") }
            jsonPath("$.status") { value(422) }
            jsonPath("$.errors[0].field") { value("destination") }
            jsonPath("$.errors[0].rejectedValue") { value(0) }
        }
    }

    @Test
    fun `출발지-도착지가 너무 멀면 422 와 TRIP_TOO_FAR 코드를 반환`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.0,"lng":127.0},"destination":{"lat":38.5,"lng":127.0}}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.title") { value("Trip Too Far") }
            jsonPath("$.code") { value("TRIP_TOO_FAR") }
            jsonPath("$.status") { value(422) }
            jsonPath("$.errors[0].field") { value("destination") }
            jsonPath("$.errors[0].rejectedValue") { exists() }
        }
    }

    @Test
    fun `facilities 도 출발지와 도착지가 같으면 422 SAME_ORIGIN_DESTINATION`() {
        mockMvc.post("/api/routes/facilities") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":127.0},"routeType":"safe"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("SAME_ORIGIN_DESTINATION") }
        }
    }

    @Test
    fun `facilities 도 origin 누락 시 422 ORIGIN_REQUIRED 를 반환`() {
        mockMvc.post("/api/routes/facilities") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245},"routeType":"safe"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("ORIGIN_REQUIRED") }
        }
    }

    @Test
    fun `origin 이 있지만 좌표 범위가 틀리면 422 가 아니라 400`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":100.0,"lng":127.0},"destination":{"lat":37.5,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `빈 본문은 400 과 Malformed Request 를 반환`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = ""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Malformed Request") }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
        }
    }

    @Test
    fun `POST 전용 엔드포인트에 GET 하면 405 와 METHOD_NOT_ALLOWED 코드 및 Allow 헤더를 반환`() {
        mockMvc.get("/api/routes/compare").andExpect {
            status { isMethodNotAllowed() }
            header { string("Allow", org.hamcrest.Matchers.containsString("POST")) }
            jsonPath("$.title") { value("Method Not Allowed") }
            jsonPath("$.code") { value("METHOD_NOT_ALLOWED") }
            jsonPath("$.status") { value(405) }
        }
    }

    @Test
    fun `지원하지 않는 Content-Type 은 415 와 UNSUPPORTED_MEDIA_TYPE 코드를 반환`() {
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.TEXT_PLAIN
            content = "lat=37.5"
        }.andExpect {
            status { isUnsupportedMediaType() }
            jsonPath("$.title") { value("Unsupported Media Type") }
            jsonPath("$.code") { value("UNSUPPORTED_MEDIA_TYPE") }
            jsonPath("$.status") { value(415) }
        }
    }

    @Test
    fun `매핑되지 않은 경로는 404 와 NOT_FOUND 코드를 반환`() {
        mockMvc.get("/api/does-not-exist").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Not Found") }
            jsonPath("$.code") { value("NOT_FOUND") }
            jsonPath("$.status") { value(404) }
        }
    }
}
