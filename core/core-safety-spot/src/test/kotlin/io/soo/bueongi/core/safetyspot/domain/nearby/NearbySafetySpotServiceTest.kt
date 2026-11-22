package io.soo.bueongi.core.safetyspot.domain.nearby

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.safetyspot.api.response.NearbySafetySpotCountResponse
import io.soo.bueongi.core.safetyspot.domain.nearby.service.NearbySafetySpotService
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NearbySafetySpotServiceTest {
    // 위도 0.001도 ≈ 111m. 중심에서 약 111m·222m(500m 안), 약 700m(500m 밖·1km 안) 좌표를 쓴다.
    private val center: GeoPoint = GeoPoint(37.55, 126.90)

    @Test
    fun `반경 500m 안 시설을 종류별로 세고, 자료가 있는데 500m 안에 없는 종류는 0으로 싣는다`() {
        val source =
            FakeSafetySpotSource(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.551, 126.90),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.552, 126.90),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.5563, 126.90),
                        ),
                    SafetySpotType.BELL to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.BELL, 37.5563, 126.90),
                        ),
                ),
            )
        val service = NearbySafetySpotService(source)

        val counts: List<NearbySafetySpotCountResponse> = service.count(center).counts

        assertThat(counts).containsExactly(
            NearbySafetySpotCountResponse(type = "cctv", count = 2),
            NearbySafetySpotCountResponse(type = "bell", count = 0),
        )
    }

    @Test
    fun `색인에 자료가 없는 종류는 응답에 싣지 않는다`() {
        val source =
            FakeSafetySpotSource(
                mapOf(
                    SafetySpotType.POLICE to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.POLICE, 37.551, 126.90),
                        ),
                ),
            )
        val service = NearbySafetySpotService(source)

        val counts: List<NearbySafetySpotCountResponse> = service.count(center).counts

        assertThat(counts).containsExactly(NearbySafetySpotCountResponse(type = "police", count = 1))
    }

    @Test
    fun `마커로 내보내지 않는 범죄주의구간은 자료가 있어도 응답에 싣지 않는다`() {
        val source =
            FakeSafetySpotSource(
                mapOf(
                    SafetySpotType.CRIME_PRONE_AREA to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CRIME_PRONE_AREA, 37.551, 126.90),
                        ),
                ),
            )
        val service = NearbySafetySpotService(source)

        assertThat(service.count(center).counts).isEmpty()
    }
}
