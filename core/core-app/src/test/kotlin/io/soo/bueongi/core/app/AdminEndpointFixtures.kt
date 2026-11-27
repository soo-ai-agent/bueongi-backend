package io.soo.bueongi.core.app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionNameCatalog
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.SafetySpotStatusService
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.share.model.ShareSession
import io.soo.bueongi.storage.db.share.model.StoredCoord
import io.soo.bueongi.storage.db.share.model.StoredLocation
import io.soo.bueongi.storage.db.share.model.StoredRoute
import io.soo.bueongi.storage.db.share.model.StoredRouteDestination
import io.soo.bueongi.storage.db.share.store.InMemoryShareSessionStore
import io.soo.bueongi.storage.db.share.store.ShareSessionStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class AdminEndpointFixtures {
    @Bean
    fun safetySpotSource(): SafetySpotSpatialSource {
        // 저장소가 같은 좌표를 하나로 세므로 지점을 달리한다.
        val cctv: ArrayList<SafetySpotPoint> = ArrayList()
        for (i in 0 until 2) {
            cctv.add(SafetySpotPoint.of(type = SafetySpotType.CCTV, lat = 37.5 + i * 0.001, lng = 127.0, regionCode = "3130000"))
        }
        return FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to cctv))
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

    companion object {
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
