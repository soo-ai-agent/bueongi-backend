package io.soo.bueongi.api.v1
import io.soo.bueongi.api.v1.request.requireDistinctEndpoints
import io.soo.bueongi.api.v1.request.requireWithinReach
import io.soo.bueongi.client.tmap.TmapRouteClient
import io.soo.bueongi.domain.route.FacilityService
import io.soo.bueongi.domain.route.RouteOption
import io.soo.bueongi.domain.route.RouteService
import io.soo.bueongi.domain.route.SafeRouteService

import io.soo.bueongi.storage.db.core.facility.FacilityRepository
import io.soo.bueongi.domain.facility.SafeDetourPlanner
import io.soo.bueongi.domain.facility.SafetyScorer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * [RouteController] 슬라이스 테스트(@WebMvcTest). 실 서비스([RouteService]/[FacilityService]/[SafeRouteService]) 를
 * import 해 compare/facilities/safe-compare 의 HTTP 계약을 고정한다:
 *  - 정상 요청 → 200 + 프론트 계약 JSON shape
 *  - destination null → 400 / origin 누락 → 422 ORIGIN_REQUIRED(복구 가능 상태로 구분)
 *  - 위경도 범위 초과(lat>90 / lng>180) → 400
 *
 * safe-compare 는 Tmap 키 미설정(테스트 환경)이라 결정론적 폴백(compare)로 동작한다 — 키 의존 외부 호출 없이
 * 폴백 계약(safe/main/fast)을 고정한다. (실 Tmap/점수/우회는 [SafeRouteServiceTest] 등에서 목으로 검증.)
 */
@WebMvcTest(RouteController::class)
@Import(
    RouteService::class,
    FacilityService::class,
    SafeRouteService::class,
    SafetyScorer::class,
    SafeDetourPlanner::class,
    FacilityRepository::class,
)
class RouteControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    // TmapRouteClient 는 RestClient.Builder(웹 슬라이스 미제공)에 의존하므로 목으로 주입한다.
    // 목의 isEnabled 기본값(false)이라 safe-compare 는 결정론적 폴백(compare) 경로를 탄다.
    @MockBean
    private lateinit var tmapRouteClient: TmapRouteClient

    private val validBody = """
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
        // 테스트 환경엔 TMAP_APP_KEY 가 없어 SafeRouteService 가 compare 로 폴백한다(점수/마커 없는 RouteOption).
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
        val body = validBody.dropLast(1) + ""","routeType":"safe"}"""
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
        // 출발지 미확보는 잘못된 입력(400)이 아니라 복구 가능 상태 → 422 로 구분(상세는 ApiExceptionHandlerTest).
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"destination":{"lat":37.5572,"lng":126.9245}}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `출발지와 도착지가 같은 좌표면 422`() {
        // 좌표는 유효하지만 같은 지점 경로는 의미 없음 → 422(상세 code 는 ApiExceptionHandlerTest).
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":37.5,"lng":127.0},"destination":{"lat":37.5,"lng":127.0}}"""
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

    // --- 비유한 좌표 차단 불변식(클라이언트 JSON 1차 유입 경계) ---
    // 직전 사이클이 Kakao 외부 응답 매핑에서 NaN/±Infinity·범위 밖 좌표를 걸렀다(toPlaceItem). 같은 위험의
    // 대칭 짝이 여기다: 클라이언트가 JSON 본문으로 보내는 origin/destination 좌표. NaN 거리는 거리 가드
    // (requireWithinReach/requireDistinctEndpoints 의 `<`/`>` 비교)를 조용히 우회해 garbage 경로/시설을
    // 만들 수 있으므로, 비유한 좌표가 라우팅 수학에 도달하기 전에 반드시 거부돼야 한다.
    // 아래 테스트는 그 불변식을 HTTP 계약으로 고정한다. (실제 거부는 두 층이 나눠 담당:
    //  - 오버플로 숫자 토큰(1e400 → ±Infinity)은 좌표 범위 검증(@DecimalMin/@DecimalMax)이 400 VALIDATION_FAILED 로 거름
    //  - 비숫자 리터럴(NaN/Infinity)은 Jackson 이 ALLOW_NON_NUMERIC_NUMBERS 기본 비활성이라 파싱 단계에서 400 MALFORMED_REQUEST)

    @Test
    fun `좌표가 양의 무한대로 오버플로(lat 1e400)면 400 VALIDATION_FAILED`() {
        // 1e400 은 문법상 유효한 JSON 숫자 토큰이라 Jackson 이 Double.POSITIVE_INFINITY 로 파싱한다.
        // @DecimalMax("90.0") 가 이를 90 초과로 보고 거른다 → 무한대 위도가 haversine 으로 새지 않는다.
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
        // -1e400 → Double.NEGATIVE_INFINITY. @DecimalMin("-180.0") 이 -180 미만으로 거른다(상한/하한 양쪽 봉쇄 확인).
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
        // NaN 은 표준 JSON 이 아니며 Jackson 의 ALLOW_NON_NUMERIC_NUMBERS 가 기본 비활성이라 파싱 단계에서 거부된다.
        // (이 기본값이 켜지면 NaN 위도가 새어 거리 가드를 우회할 수 있으므로, 그 회귀를 막는 가드 테스트다.)
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
        // Infinity 리터럴도 NaN 과 같은 이유로 JSON 파싱 단계에서 거부된다(오버플로 1e400 과 달리 숫자 토큰조차 아님).
        mockMvc.post("/api/routes/compare") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"origin":{"lat":Infinity,"lng":127.0},"destination":{"lat":37.6,"lng":126.9}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
        }
    }
}
