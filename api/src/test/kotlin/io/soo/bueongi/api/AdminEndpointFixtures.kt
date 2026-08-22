package io.soo.bueongi.api

import io.soo.bueongi.core.ads.domain.service.AdStatsService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.core.safetyspot.domain.status.service.RegionNameCatalog
import io.soo.bueongi.core.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.safetyspot.domain.status.service.SafetySpotStatusService
import io.soo.bueongi.core.enums.ExternalApiProvider
import io.soo.bueongi.core.enums.RequestCaller
import io.soo.bueongi.core.ops.domain.external.service.ExternalApiMetricsService
import io.soo.bueongi.core.ops.domain.traffic.service.RequestMetricsService
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetric
import io.soo.bueongi.storage.db.core.externalcall.ExternalCallMetricStore
import io.soo.bueongi.core.route.stats.domain.service.RouteStatsService
import io.soo.bueongi.core.ops.domain.slack.service.SlackConfigService
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
import io.soo.bueongi.client.slack.SlackAlertConfig
import io.soo.bueongi.storage.db.core.adevent.AdEvent
import io.soo.bueongi.storage.db.core.adevent.AdEventStore
import io.soo.bueongi.storage.db.core.requestmetric.AdminRequestMetricStore
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetric
import io.soo.bueongi.storage.db.core.requestmetric.RequestMetricStore
import io.soo.bueongi.storage.db.core.routestats.RouteRecommendationLog
import io.soo.bueongi.storage.db.core.routestats.RouteStatsStore
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.core.share.store.InMemoryLocationStore
import io.soo.bueongi.storage.db.core.share.store.LocationStore
import io.soo.bueongi.storage.db.core.share.model.ShareSession
import io.soo.bueongi.storage.db.core.share.model.StoredCoord
import io.soo.bueongi.storage.db.core.share.model.StoredLocation
import io.soo.bueongi.storage.db.core.share.model.StoredRoute
import io.soo.bueongi.storage.db.core.share.model.StoredRouteDestination
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** 관리자 조회 엔드포인트들이 공유하는 가짜 저장소. 각 테스트는 자기 컨트롤러만 띄우고 이것을 @Import 한다. */
class AdminEndpointFixtures {
    @TestConfiguration
    class FakeStoreConfig {

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
        @Primary
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
        fun adminRequestMetricStore(): AdminRequestMetricStore = object : AdminRequestMetricStore {
            override fun record(metric: RequestMetric) = Unit
            override fun findRecent(limit: Int): List<RequestMetric> =
                listOf(
                    RequestMetric(
                        atEpochMs = FIXED_NOW.toEpochMilli(),
                        method = "GET",
                        endpoint = "/api/admin/traffic",
                        clientId = null,
                        status = 200,
                        durationMs = 8,
                        caller = RequestCaller.ADMIN,
                    ),
                )
        }

        @Bean
        fun requestMetricsService(
            store: RequestMetricStore,
            adminStore: AdminRequestMetricStore,
        ): RequestMetricsService = RequestMetricsService(store, adminStore, FIXED_CLOCK)

        @Bean
        fun externalCallMetricStore(): ExternalCallMetricStore = object : ExternalCallMetricStore {
            override fun record(metric: ExternalCallMetric) = Unit
            override fun findRecent(limit: Int): List<ExternalCallMetric> =
                listOf(
                    ExternalCallMetric(
                        atEpochMs = FIXED_NOW.toEpochMilli(),
                        provider = ExternalApiProvider.TMAP,
                        method = "GET",
                        endpoint = "/tmap/routes/pedestrian",
                        status = 200,
                        durationMs = 320,
                        failureReason = null,
                    ),
                    ExternalCallMetric(
                        atEpochMs = FIXED_NOW.toEpochMilli(),
                        provider = ExternalApiProvider.KAKAO,
                        method = "GET",
                        endpoint = "/v2/local/search/keyword.json",
                        status = 429,
                        durationMs = 90,
                        failureReason = null,
                    ),
                )
        }

        @Bean
        fun externalApiMetricsService(store: ExternalCallMetricStore): ExternalApiMetricsService =
            ExternalApiMetricsService(store, FIXED_CLOCK)

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
        fun safetySpotSource(): SafetySpotSpatialSource =
            object : SafetySpotSpatialSource {
                private val cctv = GridSpatialIndex(
                    // 인덱스가 같은 좌표를 하나로 세므로 지점을 달리한다.
                    List(2) { i ->
                        SafetySpotPoint(
                            type = SafetySpotType.CCTV,
                            lat = 37.5 + i * 0.001,
                            lng = 127.0,
                            sigungu = "3130000",
                        )
                    },
                )
                override fun index(type: SafetySpotType): GridSpatialIndex =
                    if (type == SafetySpotType.CCTV) {
                        cctv
                    } else {
                        GridSpatialIndex(emptyList())
                    }
                override val safePaths: List<SafePath> = emptyList()
                override val freshness = SafetySpotFreshness(generatedAt = "2026-06-19T00:00:00Z", version = "2026-06-19")
            }

        @Bean
        fun safetySpotStatusService(source: SafetySpotSpatialSource): SafetySpotStatusService {
            val holder = SafetySpotRefreshStatusStore().apply {
                record(mapOf(SafetySpotType.CCTV to 2), atEpochMs = 1L)
            }
            return SafetySpotStatusService(source, holder)
        }

        @Bean
        fun regionSafetySpotStatusService(source: SafetySpotSpatialSource): RegionSafetySpotStatusService =
            RegionSafetySpotStatusService(source, RegionNameCatalog(jacksonObjectMapper()))

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
                        route = if (i == 0) {
                            FIXTURE_ROUTE
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

            /** 관리자 미리보기가 보호자 화면과 같은 경로선·목적지를 그리는지 보려면 세션에 경로가 있어야 한다. */
            val FIXTURE_ROUTE: StoredRoute = StoredRoute(
                destination = StoredRouteDestination(name = "삼성역", lat = 37.5089, lng = 127.0631),
                path = listOf(StoredCoord(37.5, 127.0), StoredCoord(37.5089, 127.0631)),
                markers = emptyList(),
                totalDistanceM = 1200,
                totalTimeS = 900,
                type = "safe",
            )
        }
    }
}
