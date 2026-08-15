package io.soo.bueongi.api

import io.soo.bueongi.core.ads.api.AdStatsController
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

@WebMvcTest(AdStatsController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminEndpointFixtures.FakeStoreConfig::class)
class AdminAdStatsEndpointTest(
    @Autowired private val mockMvc: MockMvc,
) {
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
}
