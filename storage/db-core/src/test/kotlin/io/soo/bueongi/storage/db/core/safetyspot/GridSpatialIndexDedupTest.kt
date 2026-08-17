package io.soo.bueongi.storage.db.core.safetyspot

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.store.GridSpatialIndex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GridSpatialIndexDedupTest {

    @Test
    fun `좌표가 같은 지점은 한 번만 센다`() {
        val index = GridSpatialIndex(
            listOf(
                lamp("보안등1", 37.52506736, 126.8023173),
                lamp("보안등2", 37.52506736, 126.8023173),
                lamp("보안등3", 37.52506736, 126.8023173),
            ),
        )

        assertThat(index.size).isEqualTo(1)
    }

    @Test
    fun `1m 남짓 떨어진 지점은 다른 곳으로 센다`() {
        val index = GridSpatialIndex(
            listOf(
                lamp("보안등1", 37.52506, 126.80231),
                lamp("보안등2", 37.52508, 126.80231),
            ),
        )

        assertThat(index.size).isEqualTo(2)
    }

    @Test
    fun `중복을 걷어낸 결과가 반경 조회에도 반영된다`() {
        val index = GridSpatialIndex(
            listOf(
                lamp("보안등1", 37.5250, 126.8023),
                lamp("보안등2", 37.5250, 126.8023),
            ),
        )

        assertThat(index.withinRadius(GeoPoint(37.5250, 126.8023), 100.0)).hasSize(1)
    }

    private fun lamp(name: String, lat: Double, lng: Double): SafetySpotPoint =
        SafetySpotPoint(type = SafetySpotType.LAMP, lat = lat, lng = lng, name = name)
}
