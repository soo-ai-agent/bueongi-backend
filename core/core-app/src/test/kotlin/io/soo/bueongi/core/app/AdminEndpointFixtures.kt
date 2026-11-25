package io.soo.bueongi.core.app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.client.slack.SlackAlertConfig
import io.soo.bueongi.core.admin.ops.domain.slack.service.SlackConfigService
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionNameCatalog
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.SafetySpotStatusService
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredCoord
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
import io.soo.bueongi.storage.db.share.store.InMemoryShareSessionStore
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import io.soo.bueongi.support.monitoring.AlertNotifier
import io.soo.bueongi.support.monitoring.AlertRecord
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class AdminEndpointFixtures {
    @Bean
    fun safetySpotSource(): SafetySpotSpatialSource =
        object : SafetySpotSpatialSource {
            private val cctv =
                GridSpatialIndex(
                    // 인덱스가 같은 좌표를 하나로 세므로 지점을 달리한다.
                    List(2) { i ->
                        SafetySpotPoint.of(
                            type = SafetySpotType.CCTV,
                            lat = 37.5 + i * 0.001,
                            lng = 127.0,
                            regionCode = "3130000",
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
        }

    @Bean
    fun safetySpotStatusService(source: SafetySpotSpatialSource): SafetySpotStatusService {
        val holder =
            SafetySpotRefreshStatusStore().apply {
                recordUpload(SafetySpotType.CCTV, replacedCount = 2, atEpochMs = 1L)
            }
        return SafetySpotStatusService(source, holder)
    }

    @Bean
    fun regionSafetySpotStatusService(source: SafetySpotSpatialSource): RegionSafetySpotStatusService =
        RegionSafetySpotStatusService(source, RegionNameCatalog(jacksonObjectMapper()))

    @Bean
    fun locationStore(): ShareSessionStore =
        InMemoryShareSessionStore(FIXED_CLOCK).apply {
            repeat(3) { i ->
                save(
                    ShareSession(
                        token = "tok$i",
                        ownerSecret = "sek",
                        createdAt = FIXED_NOW,
                        expiresAt = FIXED_NOW.plusSeconds(3600),
                        location =
                            if (i == 0) {
                                StoredLocation(37.5, 127.0, FIXED_NOW)
                            } else {
                                null
                            },
                        route =
                            if (i == 0) {
                                FIXTURE_ROUTE
                            } else {
                                null
                            },
                    ),
                )
            }
        }

    @Bean
    fun shareService(store: ShareSessionStore): ShareService = ShareService(store, FIXED_CLOCK, SHARE_BASE_URL)

    @Bean
    fun alertNotifier(): AlertNotifier =
        object : AlertNotifier {
            override val isEnabled: Boolean = false

            override fun notify(key: String, title: String, detail: String) = Unit

            override fun findRecent(limit: Int): List<AlertRecord> =
                listOf(AlertRecord(atEpochMs = 42L, key = "tmap-route-fallback", title = "Tmap 경로 API 실패", detail = "폴백"))
        }

    @Bean
    fun slackAlertConfig(): SlackAlertConfig = SlackAlertConfig(webhookUrl = "", throttleMinutes = 10)

    @Bean
    fun slackConfigService(config: SlackAlertConfig, notifier: AlertNotifier): SlackConfigService = SlackConfigService(config, notifier)

    companion object {
        var lastLimit: Int = -1

        val FIXED_NOW: java.time.Instant = java.time.Instant.parse("2026-06-19T12:00:00Z")
        val FIXED_CLOCK: java.time.Clock = java.time.Clock.fixed(FIXED_NOW, java.time.ZoneOffset.UTC)

        const val SHARE_BASE_URL: String = "http://localhost:3619"

        val FIXTURE_ROUTE: StoredRoute =
            StoredRoute(
                destination = StoredRouteDestination(name = "삼성역", lat = 37.5089, lng = 127.0631),
                path = listOf(StoredCoord(37.5, 127.0), StoredCoord(37.5089, 127.0631)),
                markers = emptyList(),
                totalDistanceMeters = 1200,
                totalTimeSeconds = 900,
                type = "safe",
            )
    }
}
