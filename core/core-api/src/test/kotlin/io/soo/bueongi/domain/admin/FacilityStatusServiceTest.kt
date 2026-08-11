package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.LiveFacilitySources
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FacilityStatusServiceTest {

    private class FakeSource(
        counts: Map<FacilityType, Int>,
        override val freshness: FacilityFreshness?,
    ) : FacilitySpatialSource {
        private val indices = counts.mapValues { (type, n) ->
            GridSpatialIndex((1..n).map { FacilityPoint(type = type, lat = 37.5, lng = 127.0) })
        }
        override fun index(type: FacilityType): GridSpatialIndex = indices[type] ?: GridSpatialIndex(emptyList())
        override val safePaths: List<SafePath> = emptyList()
    }

    private class FakeLiveSources(private val types: Set<FacilityType>) : LiveFacilitySources {
        override fun liveRefreshableTypes(): Set<FacilityType> = types
    }

    private val allLive = FakeLiveSources(FacilityType.entries.toSet())

    @Test
    fun `종류별 적재건수 갱신3상태 CDN신선도를 집계한다`() {
        val source = FakeSource(
            counts = mapOf(FacilityType.CCTV to 372, FacilityType.LAMP to 10, FacilityType.POLICE to 8),
            freshness = FacilityFreshness(generatedAt = "2026-06-19T00:00:00Z", version = "2026-06-19"),
        )
        val holder = FacilityRefreshStatusStore().apply {
            record(
                mapOf(
                    FacilityType.CCTV to 372,
                    FacilityType.SAFEHOUSE to null,
                ),
                atEpochMs = 1_700_000_000_000L,
            )
        }

        val summary = FacilityStatusService(source, holder, allLive).summarize()

        assertThat(summary.facilities.map { it.type })
            .containsExactly(
                FacilityType.CCTV, FacilityType.LAMP, FacilityType.BELL,
                FacilityType.SAFEHOUSE, FacilityType.POLICE, FacilityType.PEDESTRIAN_SIGNAL,
            )

        val cctv = summary.facilities.first { it.type == FacilityType.CCTV }
        assertThat(cctv.loadedCount).isEqualTo(372)
        assertThat(cctv.lastRefreshStatus).isEqualTo(FacilityRefreshStatus.REPLACED)
        assertThat(cctv.lastRefreshCount).isEqualTo(372)
        assertThat(cctv.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)
        assertThat(cctv.cdnGeneratedAt).isEqualTo("2026-06-19T00:00:00Z")
        assertThat(cctv.cdnVersion).isEqualTo("2026-06-19")

        val safehouse = summary.facilities.first { it.type == FacilityType.SAFEHOUSE }
        assertThat(safehouse.loadedCount).isEqualTo(0)
        assertThat(safehouse.lastRefreshStatus).isEqualTo(FacilityRefreshStatus.KEPT)
        assertThat(safehouse.lastRefreshCount).isNull()
        assertThat(safehouse.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)

        val lamp = summary.facilities.first { it.type == FacilityType.LAMP }
        assertThat(lamp.loadedCount).isEqualTo(10)
        assertThat(lamp.lastRefreshStatus).isEqualTo(FacilityRefreshStatus.NEVER)
        assertThat(lamp.lastRefreshAtMs).isNull()
        assertThat(lamp.lastRefreshCount).isNull()
    }

    @Test
    fun `갱신 이력이 전혀 없으면 모두 never 이고 CDN 신선도 미상이면 null`() {
        val source = FakeSource(counts = mapOf(FacilityType.CCTV to 5), freshness = null)

        val summary = FacilityStatusService(source, FacilityRefreshStatusStore(), allLive).summarize()

        assertThat(summary.facilities).allSatisfy { f ->
            assertThat(f.lastRefreshStatus).isEqualTo(FacilityRefreshStatus.NEVER)
            assertThat(f.lastRefreshAtMs).isNull()
            assertThat(f.cdnGeneratedAt).isNull()
            assertThat(f.cdnVersion).isNull()
        }
        assertThat(summary.facilities.first { it.type == FacilityType.CCTV }.loadedCount).isEqualTo(5)
    }

    @Test
    fun `라이브 원천이 없는 종류는 never 가 아니라 skipped 로 구분한다`() {
        val source = FakeSource(counts = mapOf(FacilityType.PEDESTRIAN_SIGNAL to 2), freshness = null)
        val onlyCctvLive = FakeLiveSources(setOf(FacilityType.CCTV))

        val summary = FacilityStatusService(source, FacilityRefreshStatusStore(), onlyCctvLive).summarize()

        assertThat(summary.facilities.first { it.type == FacilityType.CCTV }.lastRefreshStatus)
            .isEqualTo(FacilityRefreshStatus.NEVER)
        assertThat(summary.facilities.first { it.type == FacilityType.PEDESTRIAN_SIGNAL }.lastRefreshStatus)
            .isEqualTo(FacilityRefreshStatus.SKIPPED)
        assertThat(summary.facilities.first { it.type == FacilityType.POLICE }.lastRefreshStatus)
            .isEqualTo(FacilityRefreshStatus.SKIPPED)
    }
}
