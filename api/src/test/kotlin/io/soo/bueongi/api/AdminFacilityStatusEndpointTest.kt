package io.soo.bueongi.api

import io.soo.bueongi.core.facility.api.FacilityStatusController
import io.soo.bueongi.core.share.domain.LocationView
import io.soo.bueongi.core.ads.domain.AdStatsService
import io.soo.bueongi.core.ops.domain.apistatus.ApiStatusService
import io.soo.bueongi.core.facility.domain.status.FacilityStatusService
import io.soo.bueongi.core.ops.domain.traffic.RequestMetricsService
import io.soo.bueongi.core.route.stats.domain.RouteStatsService
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

@WebMvcTest(FacilityStatusController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminFacilityStatusEndpointTest(
    @Autowired private val mockMvc: MockMvc,
) {
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
}
