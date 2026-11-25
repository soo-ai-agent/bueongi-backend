package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetySpotSourcesTest {
    private fun lamp(lat: Double, regionCode: String): SafetySpotPoint =
        SafetySpotPoint.of(SafetySpotType.SECURITY_LAMP, lat, 128.0, regionCode = regionCode)

    @Test
    fun `업로드에 이미 있는 지역의 API 조각은 색인에 넣지 않는다`() {
        val uploaded: SafetySpotPoint = lamp(35.0, regionCode = "5340000")
        val apiSameRegion: SafetySpotPoint = lamp(35.1, regionCode = "5340000")
        val apiOtherRegion: SafetySpotPoint = lamp(36.0, regionCode = "5170000")

        val sources: SafetySpotSources =
            SafetySpotSources.EMPTY
                .withFiles(listOf(uploaded))
                .withRegion("5340000", listOf(apiSameRegion))
                .withRegion("5170000", listOf(apiOtherRegion))

        assertThat(sources.index.all).containsExactlyInAnyOrder(uploaded, apiOtherRegion)
    }
}
