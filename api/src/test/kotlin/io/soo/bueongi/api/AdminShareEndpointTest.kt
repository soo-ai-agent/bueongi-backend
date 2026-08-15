package io.soo.bueongi.api

import io.soo.bueongi.core.share.api.ShareAdminController
import io.soo.bueongi.core.share.domain.LocationView
import io.soo.bueongi.core.ads.domain.AdStatsService
import io.soo.bueongi.core.ops.domain.apistatus.ApiStatusService
import io.soo.bueongi.core.facility.domain.status.FacilityStatusService
import io.soo.bueongi.core.ops.domain.traffic.RequestMetricsService
import io.soo.bueongi.core.route.domain.stats.RouteStatsService
import io.soo.bueongi.core.ops.domain.slack.SlackConfigService
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
import io.soo.bueongi.support.monitoring.SlackAlertConfig
import io.soo.bueongi.core.ops.api.ApiStatusController
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
import io.soo.bueongi.core.share.domain.ShareService
import io.soo.bueongi.storage.db.core.share.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.LocationStore
import io.soo.bueongi.storage.db.core.share.ShareSession
import io.soo.bueongi.storage.db.core.share.StoredLocation
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ShareAdminController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminShareEndpointTest(
    @Autowired private val mockMvc: MockMvc,
) {
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
}
