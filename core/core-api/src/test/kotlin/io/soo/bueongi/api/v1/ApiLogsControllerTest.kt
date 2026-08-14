package io.soo.bueongi.api.v1

import io.soo.bueongi.admin.*

import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import io.soo.bueongi.storage.db.core.apilog.ApiCallLog
import io.soo.bueongi.storage.db.core.apilog.ApiCallLogStore
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SafePath
import io.soo.bueongi.share.ShareService
import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

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
        org.assertj.core.api.Assertions.assertThat(FakeStoreConfig.lastLimit).isEqualTo(500)
    }

    @Test
    fun `api-status 는 원천 전체와 windowSize 를 반환한다`() {
        mockMvc.get("/api/admin/api-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.providers.length()") { value(5) }
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
                jsonPath("$.facilities.length()") { value(6) }
                jsonPath("$.facilities[0].type") { value("cctv") }
                jsonPath("$.facilities[0].loadedCount") { value(2) }
                jsonPath("$.facilities[0].lastRefreshStatus") { value("replaced") }
                jsonPath("$.facilities[0].lastRefreshCount") { value(2) }
                jsonPath("$.facilities[0].cdnVersion") { value("2026-06-19") }
            }
    }

    @Test
    fun `route-stats 는 엔드포인트 2종과 창 크기를 반환한다`() {
        mockMvc.get("/api/admin/route-stats")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(1) }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.byEndpoint.length()") { value(2) }
                jsonPath("$.byEndpoint[0].endpoint") { value("compare") }
                jsonPath("$.byEndpoint[0].count") { value(1) }
                jsonPath("$.byEndpoint[1].endpoint") { value("safe-compare") }
                jsonPath("$.byPreference[0].preference") { value("safe") }
                jsonPath("$.topDestinations[0].name") { value("강남역") }
                jsonPath("$.hourlyTrend.length()") { value(24) }
            }
    }

    @Test
    fun `traffic 은 엔드포인트 집계와 예측 사용자 시간대 일자 추이를 반환한다`() {
        mockMvc.get("/api/admin/traffic")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalCalls") { value(1) }
                jsonPath("$.windowSize") { value(1) }
                jsonPath("$.byEndpoint[0].method") { value("GET") }
                jsonPath("$.byEndpoint[0].endpoint") { value("/api/places/search") }
                jsonPath("$.byEndpoint[0].count") { value(1) }
                jsonPath("$.byEndpoint[0].successRate") { value(1.0) }
                jsonPath("$.byEndpoint[0].distinctClients") { value(1) }
                jsonPath("$.estimatedUsers.today") { value(1) }
                jsonPath("$.estimatedUsers.callsWithoutClientId") { value(0) }
                jsonPath("$.hourlyCalls.length()") { value(24) }
                jsonPath("$.dailyActiveUsers.length()") { value(7) }
            }
    }

    @Test
    fun `ad-stats 는 총계 오늘 지면별 카운트 시간대 트렌드를 반환한다`() {
        mockMvc.get("/api/admin/ad-stats")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(2) }
                jsonPath("$.today") { isNumber() }
                jsonPath("$.byPlacement.interstitial") { value(1) }
                jsonPath("$.byPlacement.native") { value(1) }
                jsonPath("$.hourlyTrend.length()") { value(24) }
            }
    }

    @Test
    fun `share-status 는 활성 세션 수를 반환한다`() {
        mockMvc.get("/api/admin/share-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.activeSessions") { value(3) }
            }
    }

    @Test
    fun `share-sessions 는 활성 공유 목록을 계약 형태로 반환한다`() {
        mockMvc.get("/api/admin/share-sessions")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.sessions.length()") { value(3) }
                jsonPath("$.sessions[0].token") { value("tok0") }
                jsonPath("$.sessions[0].shareUrl") { value("http://localhost:3619/share/tok0") }
                jsonPath("$.sessions[0].lat") { value(37.5) }
                jsonPath("$.sessions[0].lng") { value(127.0) }
                jsonPath("$.sessions[0].updatedAtMs") { isNumber() }
                jsonPath("$.sessions[0].ttlSeconds") { value(3600) }
                jsonPath("$.sessions[0].viewerActive") { value(false) }
                jsonPath("$.sessions[1].token") { value("tok1") }
                jsonPath("$.sessions[1].lat") { value(null) }
                jsonPath("$.sessions[1].updatedAtMs") { value(null) }
                jsonPath("$.sessions[0].ownerSecret") { doesNotExist() }
                jsonPath("$.sessions[0].owner_secret") { doesNotExist() }
                jsonPath("$.sessions[0].route") { doesNotExist() }
            }
    }

    @Test
    fun `share-sessions token location 은 200 과 snake_case LocationView 를 반환한다`() {
        mockMvc.get("/api/admin/share-sessions/tok0/location")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.lat") { value(37.5) }
                jsonPath("$.lng") { value(127.0) }
                jsonPath("$.updated_at") { exists() }
                jsonPath("$.expired") { value(false) }
                jsonPath("$.remaining_m") { value(null) }
                jsonPath("$.remaining_s") { value(null) }
                jsonPath("$.updatedAt") { doesNotExist() }
            }
    }

    @Test
    fun `share-sessions token location 반복 열람은 viewerActive 를 올리지 않는다(비집계)`() {
        repeat(3) {
            mockMvc.get("/api/admin/share-sessions/tok0/location").andExpect { status { isOk() } }
        }
        mockMvc.get("/api/admin/share-sessions")
            .andExpect {
                status { isOk() }
                jsonPath("$.sessions[0].token") { value("tok0") }
                jsonPath("$.sessions[0].viewerActive") { value(false) }
            }
    }

    @Test
    fun `없는 token 의 share-sessions location 은 200 expired=true(공개 조회와 동일 규약)`() {
        mockMvc.get("/api/admin/share-sessions/does-not-exist/location")
            .andExpect {
                status { isOk() }
                jsonPath("$.expired") { value(true) }
                jsonPath("$.lat") { value(null) }
            }
    }

    @Test
    fun `alerts 는 최근 장애 이력을 배열로 반환한다`() {
        mockMvc.get("/api/admin/alerts")
            .andExpect {
                status { isOk() }
                jsonPath("$.alerts[0].key") { value("tmap-route-fallback") }
                jsonPath("$.alerts[0].title") { value("Tmap 경로 API 실패") }
                jsonPath("$.alerts[0].atEpochMs") { isNumber() }
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
        fun routeStatsStore(): RouteStatsStore = object : RouteStatsStore {
            override fun record(log: RouteRecommendationLog) = Unit
            override fun findRecent(limit: Int): List<RouteRecommendationLog> =
                listOf(
                    RouteRecommendationLog(
                        atEpochMs = System.currentTimeMillis(),
                        endpoint = "compare",
                        destinationName = "강남역",
                        preference = SafetyPreference.SAFE,
                    ),
                )
        }

        @Bean
        fun routeStatsService(store: RouteStatsStore): RouteStatsService = RouteStatsService(store)

        @Bean
        fun requestMetricStore(): RequestMetricStore = object : RequestMetricStore {
            override fun record(metric: RequestMetric) = Unit
            override fun findRecent(limit: Int): List<RequestMetric> =
                listOf(
                    RequestMetric(
                        atEpochMs = System.currentTimeMillis(),
                        method = "GET",
                        endpoint = "/api/places/search",
                        clientId = "anon-1",
                        status = 200,
                        durationMs = 15,
                    ),
                )
        }

        @Bean
        fun requestMetricsService(store: RequestMetricStore): RequestMetricsService = RequestMetricsService(store)

        @Bean
        fun adEventStore(): AdEventStore = object : AdEventStore {
            override fun record(event: AdEvent) = Unit
            override fun findRecent(limit: Int): List<AdEvent> =
                listOf(
                    AdEvent(atEpochMs = System.currentTimeMillis(), placement = "interstitial"),
                    AdEvent(atEpochMs = System.currentTimeMillis(), placement = "native"),
                )
        }

        @Bean
        fun adStatsService(store: AdEventStore): AdStatsService = AdStatsService(store)

        @Bean
        fun facilityStatusService(): FacilityStatusService {
            val source = object : FacilitySpatialSource {
                private val cctv: GridSpatialIndex = GridSpatialIndex(
                    List(2) { FacilityPoint(type = FacilityType.CCTV, lat = 37.5, lng = 127.0) },
                )
                override fun index(type: FacilityType): GridSpatialIndex =
                    if (type == FacilityType.CCTV) {
                        cctv
                    } else {
                        GridSpatialIndex(emptyList())
                    }
                override val safePaths: List<SafePath> = emptyList()
                override val freshness: FacilityFreshness = FacilityFreshness(generatedAt = "2026-06-19T00:00:00Z", version = "2026-06-19")
            }
            val holder: FacilityRefreshStatusStore = FacilityRefreshStatusStore().apply {
                record(mapOf(FacilityType.CCTV to 2), atEpochMs = 1L)
            }
            return FacilityStatusService(source, holder)
        }

        @Bean
        fun locationStore(): LocationStore = InMemoryLocationStore(FIXED_CLOCK).apply {
            repeat(3) { i ->
                save(
                    ShareSession(
                        token = "tok$i",
                        ownerSecret = "sek",
                        createdAt = FIXED_NOW,
                        expiresAt = FIXED_NOW.plusSeconds(3600),
                        location = if (i == 0) {
                            StoredLocation(37.5, 127.0, FIXED_NOW)
                        } else {
                            null
                        },
                    ),
                )
            }
        }

        @Bean
        fun shareService(store: LocationStore): ShareService = ShareService(store, FIXED_CLOCK, SHARE_BASE_URL)

        @Bean
        fun alertNotifier(): AlertNotifier = object : AlertNotifier {
            override val isEnabled: Boolean = false
            override fun notify(key: String, title: String, detail: String) = Unit
            override fun findRecentAlerts(limit: Int): List<AlertRecord> =
                listOf(AlertRecord(atEpochMs = 42L, key = "tmap-route-fallback", title = "Tmap 경로 API 실패", detail = "폴백"))
        }

        @Bean
        fun slackAlertConfig(): SlackAlertConfig = SlackAlertConfig(webhookUrl = "", throttleMinutes = 10)

        @Bean
        fun slackConfigService(config: SlackAlertConfig, notifier: AlertNotifier): SlackConfigService =
            SlackConfigService(config, notifier)

        companion object {
            var lastLimit: Int = -1

            val FIXED_NOW: java.time.Instant = java.time.Instant.parse("2026-06-19T12:00:00Z")
            val FIXED_CLOCK: java.time.Clock = java.time.Clock.fixed(FIXED_NOW, java.time.ZoneOffset.UTC)

            const val SHARE_BASE_URL: String = "http://localhost:3619"
        }
    }
}
