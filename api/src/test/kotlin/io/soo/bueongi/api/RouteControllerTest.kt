package io.soo.bueongi.api

import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.core.route.api.controller.RouteController
import io.soo.bueongi.core.route.domain.marker.service.MarkerEventPublisher
import io.soo.bueongi.core.route.domain.marker.service.RouteVisitEventPublisher
import io.soo.bueongi.core.route.domain.marker.service.RouteMarkerBuilder
import io.soo.bueongi.core.route.domain.safety.service.SafeDetourPlanner
import io.soo.bueongi.core.route.domain.safety.service.SafetyScorer
import io.soo.bueongi.core.route.domain.service.RouteCandidateCollector
import io.soo.bueongi.core.route.domain.service.RouteCandidateMerger
import io.soo.bueongi.core.route.domain.service.RouteOptionAssembler
import io.soo.bueongi.core.route.domain.service.RouteEventPublisher
import io.soo.bueongi.core.route.domain.service.RouteService
import io.soo.bueongi.core.route.domain.service.RouteTypeCatalog
import io.soo.bueongi.core.route.domain.service.SafeRouteService
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStoreConfig
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRepository
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotUploadStore
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@Tag("context")
@WebMvcTest(RouteController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    RouteService::class,
    RouteTypeCatalog::class,
    SafeRouteService::class,
    RouteCandidateCollector::class,
    RouteOptionAssembler::class,
    RouteMarkerBuilder::class,
    MarkerEventPublisher::class,
    RouteVisitEventPublisher::class,
    RouteEventPublisher::class,
    RouteStatsStoreConfig::class,
    RouteCandidateMerger::class,
    SafetyScorer::class,
    SafeDetourPlanner::class,
    SafetySpotRepository::class,
    SafetySpotUploadStore::class,
)
class RouteControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var tmapRouteClient: TmapRouteClient

    private val validBody: String =
        """
        {"origin":{"lat":37.4979,"lng":127.0276},
         "destination":{"lat":37.5572,"lng":126.9245,"name":"홍대입구역"}}
        """.trimIndent()

    @Test
    fun `좌표가 빠지면 그 필드를 짚어 400 을 낸다 - 0으로 채워 계산하지 않는다`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lng":127.0276},"destination":{"lat":37.504,"lng":127.025}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("origin.lat") }
        }
    }

    @Test
    fun `compare 정상 요청은 200 과 safe main fast 3개 경로`() {
        mockMvc.post("/api/routes/safe-compare") {
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
    fun `safe-compare origin 누락이면 400`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `safetySpots 도 원거리 상한을 거른다(422 TRIP_TOO_FAR)`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.0,"lng":127.0},"destination":{"lat":38.5,"lng":127.0},"routeType":"safe"}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `destination 좌표 누락이면 400`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.4979,"lng":127.0276},"destination":{"name":"x"}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `경도 범위 초과(lng 200)면 400`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":200.0}}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `좌표가 양의 무한대로 오버플로(lat 1e400)면 400 VALIDATION_FAILED`() {
        mockMvc.post("/api/routes/safe-compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":1e400,"lng":127.0},"destination":{"lat":37.5,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("origin.lat") }
        }
    }
}
