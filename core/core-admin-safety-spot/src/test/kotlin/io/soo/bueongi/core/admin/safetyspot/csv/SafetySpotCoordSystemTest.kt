package io.soo.bueongi.core.admin.safetyspot.csv

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.safetyspot.domain.csv.model.SafetySpotCoordSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafetySpotCoordSystemTest {
    private val mapper: ObjectMapper = ObjectMapper()

    @Test
    fun `와이어 값이 wgs84 epsg5179 epsg5174 로 직렬화되고 되읽힌다`() {
        roundTrip(SafetySpotCoordSystem.WGS84, "wgs84")
        roundTrip(SafetySpotCoordSystem.EPSG5179, "epsg5179")
        roundTrip(SafetySpotCoordSystem.EPSG5174, "epsg5174")
    }

    private fun roundTrip(value: SafetySpotCoordSystem, wire: String) {
        assertThat(mapper.writeValueAsString(value)).isEqualTo("\"$wire\"")
        assertThat(mapper.readValue("\"$wire\"", SafetySpotCoordSystem::class.java)).isEqualTo(value)
    }
}
