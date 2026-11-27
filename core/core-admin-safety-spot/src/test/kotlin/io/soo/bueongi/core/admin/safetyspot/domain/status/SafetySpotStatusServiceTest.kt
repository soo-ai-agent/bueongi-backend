package io.soo.bueongi.core.admin.safetyspot.domain.status

import io.soo.bueongi.core.admin.safetyspot.domain.status.service.SafetySpotStatusService
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import io.soo.bueongi.storage.db.safetyspot.store.SafetySpotRefreshStatusStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetySpotStatusServiceTest {
    // 좌표가 겹치면 색인이 하나로 접으므로 lng 로 목록끼리 떨어뜨린다.
    private fun points(type: SafetySpotType, count: Int, lng: Double = 127.0): List<SafetySpotPoint> =
        (1..count).map { i -> SafetySpotPoint.of(type = type, lat = 37.5 + i * 0.001, lng = lng) }

    @Test
    fun `종류별 적재건수와 갱신 이력을 집계한다`() {
        val source =
            FakeSafetySpotSource(
                mapOf(
                    SafetySpotType.CCTV to points(SafetySpotType.CCTV, 372),
                    SafetySpotType.STREET_LAMP to points(SafetySpotType.STREET_LAMP, 10),
                    SafetySpotType.POLICE to points(SafetySpotType.POLICE, 8),
                ),
            )
        val holder =
            SafetySpotRefreshStatusStore().apply {
                recordUpload(SafetySpotType.CCTV, replacedCount = 372, atEpochMs = 1_700_000_000_000L)
            }

        val summary = SafetySpotStatusService(source, holder).find()

        assertThat(summary.safetySpots.map { it.type })
            .containsExactly(
                SafetySpotType.CCTV,
                SafetySpotType.STREET_LAMP,
                SafetySpotType.SECURITY_LAMP,
                SafetySpotType.BELL,
                SafetySpotType.SAFEHOUSE,
                SafetySpotType.POLICE,
                SafetySpotType.PEDESTRIAN_SIGNAL,
                SafetySpotType.CRIME_PRONE_AREA,
            )

        val cctv = summary.safetySpots.first { it.type == SafetySpotType.CCTV }
        assertThat(cctv.loadedCount).isEqualTo(372)
        assertThat(cctv.lastRefreshCount).isEqualTo(372)
        assertThat(cctv.lastRefreshAtMs).isEqualTo(1_700_000_000_000L)

        val lamp = summary.safetySpots.first { it.type == SafetySpotType.STREET_LAMP }
        assertThat(lamp.loadedCount).isEqualTo(10)
        assertThat(lamp.lastRefreshAtMs).isNull()
        assertThat(lamp.lastRefreshCount).isNull()
    }

    @Test
    fun `갱신 이력이 전혀 없으면 시각과 건수가 null 이다`() {
        val source = FakeSafetySpotSource(mapOf(SafetySpotType.CCTV to points(SafetySpotType.CCTV, 5)))

        val summary = SafetySpotStatusService(source, SafetySpotRefreshStatusStore()).find()

        assertThat(summary.safetySpots).allSatisfy { f ->
            assertThat(f.lastRefreshAtMs).isNull()
            assertThat(f.lastRefreshCount).isNull()
        }
        assertThat(summary.safetySpots.first { it.type == SafetySpotType.CCTV }.loadedCount).isEqualTo(5)
    }

    @Test
    fun `적재된 점을 출처별로 나눠 센다 — API 수집분과 파일분의 합이 적재 수다`() {
        val source =
            FakeSafetySpotSource(
                points = mapOf(SafetySpotType.SECURITY_LAMP to points(SafetySpotType.SECURITY_LAMP, 6)),
                apiPoints = mapOf(SafetySpotType.SECURITY_LAMP to points(SafetySpotType.SECURITY_LAMP, 4, lng = 126.9)),
            )

        val summary = SafetySpotStatusService(source, SafetySpotRefreshStatusStore()).find()

        val lamp = summary.safetySpots.first { it.type == SafetySpotType.SECURITY_LAMP }
        assertThat(lamp.apiCount).isEqualTo(4)
        assertThat(lamp.fileCount).isEqualTo(6)
        assertThat(lamp.loadedCount).isEqualTo(10)
    }

    @Test
    fun `업로드한 종류만 갱신 이력이 남는다`() {
        val source = FakeSafetySpotSource(mapOf(SafetySpotType.PEDESTRIAN_SIGNAL to points(SafetySpotType.PEDESTRIAN_SIGNAL, 2)))
        val holder =
            SafetySpotRefreshStatusStore().apply {
                recordUpload(SafetySpotType.CCTV, replacedCount = 315_459, atEpochMs = 1L)
            }

        val summary = SafetySpotStatusService(source, holder).find()

        assertThat(summary.safetySpots.first { it.type == SafetySpotType.CCTV }.lastRefreshAtMs).isEqualTo(1L)
        assertThat(summary.safetySpots.first { it.type == SafetySpotType.PEDESTRIAN_SIGNAL }.lastRefreshAtMs).isNull()
        assertThat(summary.safetySpots.first { it.type == SafetySpotType.POLICE }.lastRefreshAtMs).isNull()
    }
}
