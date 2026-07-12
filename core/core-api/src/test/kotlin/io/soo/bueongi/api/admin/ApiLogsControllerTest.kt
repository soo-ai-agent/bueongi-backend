package io.soo.bueongi.api.admin

import io.soo.bueongi.domain.admin.ApiStatusService
import io.soo.bueongi.domain.admin.FacilityStatusService
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SafePath
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
 * [AdminController] api-logs 슬라이스 테스트(@WebMvcTest). 보안 필터는 끄고(실 게이트는 AdminAuthGateTest 가 커버)
 * GET /api/admin/api-logs 의 HTTP 계약(200 + 접속 내역 JSON shape, provider/limit 파라미터 위임)만 고정한다.
 */
@WebMvcTest(AdminController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiLogsControllerTest.FakeStoreConfig::class)
class ApiLogsControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `접속 내역을 200 과 배열로 반환한다`() {
        mockMvc.get("/api/admin/api-logs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$[0].provider") { value("KAKAO") }
                jsonPath("$[0].endpoint") { value("search") }
                jsonPath("$[0].success") { value(true) }
                jsonPath("$[0].durationMs") { isNumber() }
            }
    }

    @Test
    fun `limit 은 상한(500)으로 절삭되어 저장소에 전달된다`() {
        mockMvc.get("/api/admin/api-logs") { param("limit", "9999") }
            .andExpect { status { isOk() } }
        // FakeStore 가 전달받은 limit 을 기록 — coerceIn(1,500) 결과 검증.
        org.assertj.core.api.Assertions.assertThat(FakeStoreConfig.lastLimit).isEqualTo(500)
    }

    @Test
    fun `api-status 는 원천 4종과 windowSize 를 반환한다`() {
        mockMvc.get("/api/admin/api-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.providers.length()") { value(4) }
                jsonPath("$.providers[0].provider") { value("KAKAO") }
                jsonPath("$.providers[0].total") { value(1) }
                jsonPath("$.providers[0].successRate") { value(1.0) }
            }
    }

    @Test
    fun `facility-status 는 종류별 적재건수 갱신결과 CDN신선도를 반환한다`() {
        mockMvc.get("/api/admin/facility-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.facilities.length()") { value(5) }
                jsonPath("$.facilities[0].type") { value("cctv") }
                jsonPath("$.facilities[0].loadedCount") { value(2) }
                jsonPath("$.facilities[0].lastRefreshStatus") { value("replaced") }
                jsonPath("$.facilities[0].lastRefreshCount") { value(2) }
                jsonPath("$.facilities[0].cdnVersion") { value("2026-06-19") }
            }
    }

    @TestConfiguration
    class FakeStoreConfig {
        @Bean
        fun apiCallLogStore(): ApiCallLogStore = object : ApiCallLogStore {
            override fun record(log: ApiCallLog) = Unit
            override fun findRecent(provider: String?, limit: Int): List<ApiCallLog> {
                lastLimit = limit
                return listOf(
                    ApiCallLog(atEpochMs = 1L, provider = "KAKAO", endpoint = "search", success = true, durationMs = 12),
                )
            }
        }

        @Bean
        fun apiStatusService(store: ApiCallLogStore): ApiStatusService = ApiStatusService(store)

        @Bean
        fun facilityStatusService(): FacilityStatusService {
            val source = object : FacilitySpatialSource {
                private val cctv = GridSpatialIndex(
                    List(2) { FacilityPoint(type = FacilityType.CCTV, lat = 37.5, lng = 127.0) },
                )
                override fun index(type: FacilityType): GridSpatialIndex =
                    if (type == FacilityType.CCTV) cctv else GridSpatialIndex(emptyList())
                override val safePaths: List<SafePath> = emptyList()
                override val freshness = FacilityFreshness(generatedAt = "2026-06-19T00:00:00Z", version = "2026-06-19")
            }
            val holder = FacilityRefreshStatusStore().apply {
                record(mapOf(FacilityType.CCTV to 2), atEpochMs = 1L)
            }
            return FacilityStatusService(source, holder)
        }

        companion object {
            var lastLimit: Int = -1
        }
    }
}
