package io.soo.bueongi.api

import io.soo.bueongi.storage.db.core.facility.FacilityRepository
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyScorer
import io.soo.bueongi.domain.route.RouteFacilityService
import io.soo.bueongi.api.v1.RouteController
import io.soo.bueongi.domain.route.RouteService
import io.soo.bueongi.domain.route.SafeRouteService
import io.soo.bueongi.domain.route.RouteCandidateMerger
import io.soo.bueongi.domain.route.RouteMarkerBuilder
import io.soo.bueongi.client.tmap.TmapRouteClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * [ApiControllerAdvice] 슬라이스 테스트. 검증 실패 400 응답이 표준 스키마(ProblemDetail + errors[])를
 * 따르는지 고정한다 — 프론트가 status 코드뿐 아니라 field/message/rejectedValue 를 파싱할 수 있어야 한다.
 * [RouteController] 를 통해 실제 `@Valid` 흐름을 태운다.
 */
@WebMvcTest(RouteController::class)
@Import(
    RouteService::class,
    RouteFacilityService::class,
    ApiControllerAdvice::class,
    SafeRouteService::class,
    RouteMarkerBuilder::class,
    RouteCandidateMerger::class,
    SafetyScorer::class,
    SafeDetourPlanner::class,
    FacilityRepository::class,
)
class ApiExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    // TmapRouteClient 는 RestClient.Builder(웹 슬라이스 미제공) 의존이라 목으로 주입한다(@Valid 흐름만 검증).
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
        // 출발지 미확보(위치 권한 거부 등)는 "잘못된 입력"(400)이 아니라 복구 가능한 422 로 구분된다.
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
        // 좌표는 유효하지만(400 아님) 같은 지점 경로는 의미 없는 복구 가능 상태 → 422 + destination 필드 errors.
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
        // 좌표는 유효(범위 내)하지만 도보 귀가로는 비현실적 원거리(위도 1.5도 ≈ 167km) → 422 + destination errors.
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
        // 값이 존재하면 @Valid 가 우선 — 미확보(422)와 잘못된 입력(400)을 분명히 구분한다.
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
        // 프레임워크 레벨 에러도 "모든 에러 응답에 code" 계약을 지켜야 한다(Spring 기본은 code 없음).
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
        // 오타·폐기된 엔드포인트 호출도 "모든 에러 응답에 code" 계약을 지켜야 한다(Spring 기본은 code 없음).
        mockMvc.get("/api/does-not-exist").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Not Found") }
            jsonPath("$.code") { value("NOT_FOUND") }
            jsonPath("$.status") { value(404) }
        }
    }
}
