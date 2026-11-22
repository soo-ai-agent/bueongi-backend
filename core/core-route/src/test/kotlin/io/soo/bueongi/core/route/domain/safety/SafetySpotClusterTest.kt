package io.soo.bueongi.core.route.domain.safety

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.safety.model.SafetySpotCluster
import io.soo.bueongi.storage.db.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetySpotClusterTest {
    @Test
    fun `대표 좌표는 평균점이 아니라 평균점에 가장 가까운 실제 시설 위치다`() {
        val spots: List<SafetySpotPoint> =
            listOf(
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.000),
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.002),
                SafetySpotPoint.of(SafetySpotType.CCTV, 37.5, 127.010),
            )

        val cluster: SafetySpotCluster = SafetySpotCluster.from(spots)

        assertThat(cluster.center).isEqualTo(GeoPoint(37.5, 127.002))
    }
}
