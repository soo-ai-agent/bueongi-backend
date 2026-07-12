package io.soo.bueongi.domain.admin

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import io.soo.bueongi.storage.db.core.facility.GridSpatialIndex
import io.soo.bueongi.storage.db.core.facility.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [FacilityStatusService] 집계 정확성 — 적재 건수(공간 인덱스 size)·마지막 갱신 결과(3상태)·CDN 신선도를
 * 종류별로 모은다. 저장소·홀더는 얇은 페이크로 주입한다.
 */
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

    @Test
    fun `종류별 적재건수 갱신3상태 CDN신선도를 집계한다`() {
        val source = FakeSource(
            counts = mapOf(FacilityType.CCTV to 372, FacilityType.LAMP to 10, FacilityType.POLICE to 8),
            freshness = FacilityFreshness(generatedAt = "2026-06-19T00:00:00Z", version = "2026-06-19"),
        )
        val holder = FacilityRefreshStatusStore().apply {
            record(
                mapOf(
                    FacilityType.CCTV to 372,   // replaced
                    FacilityType.SAFEHOUSE to null, // kept (수집 실패·0건 유지)
                    // BELL·POLICE·LAMP 미포함 → never
                ),
                atEpochMs = 1_700_000_000_000L,
            )
        }

        val summary = FacilityStatusService(source, holder).summarize()

        // 5종 전부 cdnType 문자열로 노출.
        assertThat(summary.facilities.map { it.type })
            .containsExactly("cctv", "lamp", "emergency_bell", "safehouse", "police")

        val cctv = summary.facilities.first { it.type == "cctv" }
        assertThat(cctv.loadedCount).isEqualTo(372)
        assertThat(cctv.lastRefreshStatus).isEqualTo("replaced")
        assertThat(cctv.lastRefreshCount).isEqualTo(372)
        assertThat(cctv.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)
        assertThat(cctv.cdnGeneratedAt).isEqualTo("2026-06-19T00:00:00Z")
        assertThat(cctv.cdnVersion).isEqualTo("2026-06-19")

        val safehouse = summary.facilities.first { it.type == "safehouse" }
        assertThat(safehouse.loadedCount).isEqualTo(0)
        assertThat(safehouse.lastRefreshStatus).isEqualTo("kept")
        assertThat(safehouse.lastRefreshCount).isNull()
        assertThat(safehouse.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)

        // lamp 는 라이브 갱신 대상이 아님 → never, 적재분(CDN)은 유지.
        val lamp = summary.facilities.first { it.type == "lamp" }
        assertThat(lamp.loadedCount).isEqualTo(10)
        assertThat(lamp.lastRefreshStatus).isEqualTo("never")
        assertThat(lamp.lastRefreshAtMs).isNull()
        assertThat(lamp.lastRefreshCount).isNull()
    }

    @Test
    fun `갱신 이력이 전혀 없으면 모두 never 이고 CDN 신선도 미상이면 null`() {
        val source = FakeSource(counts = mapOf(FacilityType.CCTV to 5), freshness = null)

        val summary = FacilityStatusService(source, FacilityRefreshStatusStore()).summarize()

        assertThat(summary.facilities).allSatisfy { f ->
            assertThat(f.lastRefreshStatus).isEqualTo("never")
            assertThat(f.lastRefreshAtMs).isNull()
            assertThat(f.cdnGeneratedAt).isNull()
            assertThat(f.cdnVersion).isNull()
        }
        assertThat(summary.facilities.first { it.type == "cctv" }.loadedCount).isEqualTo(5)
    }
}
