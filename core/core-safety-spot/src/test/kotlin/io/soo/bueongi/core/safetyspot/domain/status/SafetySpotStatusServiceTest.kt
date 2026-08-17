package io.soo.bueongi.core.safetyspot.domain.status

import io.soo.bueongi.core.safetyspot.domain.status.service.SafetySpotStatusService

import io.soo.bueongi.core.enums.SafetySpotRefreshStatus

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotRefreshStatusStore
import io.soo.bueongi.storage.db.core.safetyspot.store.SafetySpotSpatialSource
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetySpotStatusServiceTest {

    private class FakeSource(
        counts: Map<SafetySpotType, Int>,
        override val freshness: SafetySpotFreshness?,
    ) : SafetySpotSpatialSource {
        private val indices = counts.mapValues { (type, n) ->
            // 인덱스가 같은 좌표를 하나로 세므로 지점을 달리한다.
            GridSpatialIndex((1..n).map { i -> SafetySpotPoint(type = type, lat = 37.5 + i * 0.001, lng = 127.0) })
        }
        override fun index(type: SafetySpotType): GridSpatialIndex = indices[type] ?: GridSpatialIndex(emptyList())
        override val safePaths: List<SafePath> = emptyList()
    }

    @Test
    fun `종류별 적재건수 갱신3상태 CDN신선도를 집계한다`() {
        val source = FakeSource(
            counts = mapOf(SafetySpotType.CCTV to 372, SafetySpotType.LAMP to 10, SafetySpotType.POLICE to 8),
            freshness = SafetySpotFreshness(generatedAt = "2026-06-19T00:00:00Z", version = "2026-06-19"),
        )
        val holder = SafetySpotRefreshStatusStore().apply {
            record(
                mapOf(
                    SafetySpotType.CCTV to 372,
                    SafetySpotType.SAFEHOUSE to null,
                ),
                atEpochMs = 1_700_000_000_000L,
            )
        }

        val summary = SafetySpotStatusService(source, holder).summarize()

        assertThat(summary.safetySpots.map { it.type })
            .containsExactly(
                SafetySpotType.CCTV, SafetySpotType.LAMP, SafetySpotType.SECURITY_LAMP, SafetySpotType.BELL,
                SafetySpotType.SAFEHOUSE, SafetySpotType.POLICE, SafetySpotType.PEDESTRIAN_SIGNAL,
            )

        val cctv = summary.safetySpots.first { it.type == SafetySpotType.CCTV }
        assertThat(cctv.loadedCount).isEqualTo(372)
        assertThat(cctv.lastRefreshStatus).isEqualTo(SafetySpotRefreshStatus.REPLACED)
        assertThat(cctv.lastRefreshCount).isEqualTo(372)
        assertThat(cctv.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)
        assertThat(cctv.cdnGeneratedAt).isEqualTo("2026-06-19T00:00:00Z")
        assertThat(cctv.cdnVersion).isEqualTo("2026-06-19")

        val safehouse = summary.safetySpots.first { it.type == SafetySpotType.SAFEHOUSE }
        assertThat(safehouse.loadedCount).isEqualTo(0)
        assertThat(safehouse.lastRefreshStatus).isEqualTo(SafetySpotRefreshStatus.KEPT)
        assertThat(safehouse.lastRefreshCount).isNull()
        assertThat(safehouse.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)

        val lamp = summary.safetySpots.first { it.type == SafetySpotType.LAMP }
        assertThat(lamp.loadedCount).isEqualTo(10)
        assertThat(lamp.lastRefreshStatus).isEqualTo(SafetySpotRefreshStatus.SKIPPED)
        assertThat(lamp.lastRefreshAtMs).isNull()
        assertThat(lamp.lastRefreshCount).isNull()
    }

    @Test
    fun `갱신 이력이 전혀 없으면 모두 skipped 이고 CDN 신선도 미상이면 null`() {
        val source = FakeSource(counts = mapOf(SafetySpotType.CCTV to 5), freshness = null)

        val summary = SafetySpotStatusService(source, SafetySpotRefreshStatusStore()).summarize()

        assertThat(summary.safetySpots).allSatisfy { f ->
            assertThat(f.lastRefreshStatus).isEqualTo(SafetySpotRefreshStatus.SKIPPED)
            assertThat(f.lastRefreshAtMs).isNull()
            assertThat(f.cdnGeneratedAt).isNull()
            assertThat(f.cdnVersion).isNull()
        }
        assertThat(summary.safetySpots.first { it.type == SafetySpotType.CCTV }.loadedCount).isEqualTo(5)
    }

    @Test
    fun `업로드한 종류만 교체로 표시되고 나머지는 CDN 파일 그대로라 skipped 다`() {
        val source = FakeSource(counts = mapOf(SafetySpotType.PEDESTRIAN_SIGNAL to 2), freshness = null)
        val holder = SafetySpotRefreshStatusStore().apply {
            recordUpload(SafetySpotType.CCTV, replacedCount = 315_459, atEpochMs = 1L)
        }

        val summary = SafetySpotStatusService(source, holder).summarize()

        assertThat(summary.safetySpots.first { it.type == SafetySpotType.CCTV }.lastRefreshStatus)
            .isEqualTo(SafetySpotRefreshStatus.REPLACED)
        assertThat(summary.safetySpots.first { it.type == SafetySpotType.PEDESTRIAN_SIGNAL }.lastRefreshStatus)
            .isEqualTo(SafetySpotRefreshStatus.SKIPPED)
        assertThat(summary.safetySpots.first { it.type == SafetySpotType.POLICE }.lastRefreshStatus)
            .isEqualTo(SafetySpotRefreshStatus.SKIPPED)
    }
}
