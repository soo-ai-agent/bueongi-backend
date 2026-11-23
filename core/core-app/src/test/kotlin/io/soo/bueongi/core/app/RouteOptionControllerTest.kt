package io.soo.bueongi.core.app

import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.common.event.ApiErrorResponded
import io.soo.bueongi.core.common.route.RoutePlannedHandler
import io.soo.bueongi.core.route.api.controller.RouteOptionController
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.safety.service.SafetySpotWaypointPlanner
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteFinder
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteOptionService
import io.soo.bueongi.core.route.domain.service.RouteTypeAssigner
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRepository
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotUploadStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@Tag("context")
@WebMvcTest(RouteOptionController::class)
@AutoConfigureMockMvc(addFilters = false)
@RecordApplicationEvents
@Import(
    RouteTypeCatalog::class,
    RouteOptionService::class,
    RouteTypeAssigner::class,
    RouteFinder::class,
    RouteOptionAssembler::class,
    RouteMarkerBuilder::class,
    RouteCandidateMerger::class,
    SafetyScorer::class,
    SafetySpotWaypointPlanner::class,
    SafetySpotRepository::class,
    SafetySpotUploadStore::class,
)
class RouteOptionControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var tmapRouteClient: TmapRouteClient

    @MockitoBean
    private lateinit var routePlannedHandler: RoutePlannedHandler

    private val validBody: String =
        """
        {"origin":{"lat":37.4979,"lng":127.0276},
         "destination":{"lat":37.5572,"lng":126.9245,"name":"홍대입구역"}}
        """.trimIndent()

    @Test
    fun `좌표가 빠지면 그 필드를 짚어 400 을 낸다 - 0으로 채워 계산하지 않는다`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lng":127.0276},"destination":{"lat":37.504,"lng":127.025}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("origin.lat") }
        }
    }

    @Test
    fun `경로 옵션 요청은 Tmap 키 없으면 503 으로 사용 불가를 알린다`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("ROUTE_SEARCH_UNAVAILABLE") }
        }
    }

    @Test
    fun `경로 탐색이 503 으로 끝나면 Tmap 경로 실패 알림을 보낸다`(applicationEvents: ApplicationEvents) {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = validBody
        }.andExpect {
            status { isServiceUnavailable() }
        }

        val errorEvents: List<ApiErrorResponded> = applicationEvents.stream(ApiErrorResponded::class.java).toList()
        assertThat(errorEvents).hasSize(1)
        assertThat(errorEvents[0].code).isEqualTo("ROUTE_SEARCH_UNAVAILABLE")
    }

    @Test
    fun `origin 누락이면 400`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `safetySpots 도 원거리 상한을 거른다(422 TRIP_TOO_FAR)`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.0,"lng":127.0},"destination":{"lat":38.5,"lng":127.0},"routeType":"safe"}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `destination 좌표 누락이면 400`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.4979,"lng":127.0276},"destination":{"name":"x"}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `경도 범위 초과(lng 200)면 400`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":200.0}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `좌표가 양의 무한대로 오버플로(lat 1e400)면 400 VALIDATION_FAILED`() {
        mockMvc.post("/api/routes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":1e400,"lng":127.0},"destination":{"lat":37.5,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("origin.lat") }
        }
    }
}
