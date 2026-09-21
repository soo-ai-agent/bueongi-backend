package io.soo.bueongi.core.route

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.enums.SafetySpotCoordSystem
import io.soo.bueongi.core.enums.SafetySpotRefreshStatus
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.core.route.domain.marker.model.SafetySpotPoiType
import io.soo.bueongi.core.route.domain.model.RouteTagVariant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WireEnumRoundTripTest {
    private val mapper = jacksonObjectMapper()

    private inline fun <reified T : Enum<T>> roundTrip(
        value: T,
        wire: String,
    ) {
        assertThat(mapper.writeValueAsString(value)).isEqualTo("\"$wire\"")
        assertThat(mapper.readValue<T>("\"$wire\"")).isEqualTo(value)
    }

    @Test
    fun `RouteType safe main fast`() {
        roundTrip(RouteType.SAFE, "safe")
        roundTrip(RouteType.MAIN, "main")
        roundTrip(RouteType.FAST, "fast")
    }

    @Test
    fun `RouteTagVariant default mint blue yellow outline`() {
        roundTrip(RouteTagVariant.DEFAULT, "default")
        roundTrip(RouteTagVariant.MINT, "mint")
        roundTrip(RouteTagVariant.BLUE, "blue")
        roundTrip(RouteTagVariant.YELLOW, "yellow")
        roundTrip(RouteTagVariant.OUTLINE, "outline")
    }

    @Test
    fun `SafetySpotPoiType 전 종류`() {
        roundTrip(SafetySpotPoiType.START, "start")
        roundTrip(SafetySpotPoiType.END, "end")
        roundTrip(SafetySpotPoiType.CCTV, "cctv")
        roundTrip(SafetySpotPoiType.BELL, "bell")
        roundTrip(SafetySpotPoiType.STORE, "store")
        roundTrip(SafetySpotPoiType.POLICE, "police")
        roundTrip(SafetySpotPoiType.SAFEHOUSE, "safehouse")
        roundTrip(SafetySpotPoiType.STREET_LAMP, "lamp")
    }

    @Test
    fun `SafetyPreference balanced safe safest`() {
        roundTrip(SafetyPreference.BALANCED, "balanced")
        roundTrip(SafetyPreference.SAFE, "safe")
        roundTrip(SafetyPreference.SAFEST, "safest")
    }

    @Test
    fun `SafetySpotCoordSystem wgs84 epsg5179 epsg5174`() {
        roundTrip(SafetySpotCoordSystem.WGS84, "wgs84")
        roundTrip(SafetySpotCoordSystem.EPSG5179, "epsg5179")
        roundTrip(SafetySpotCoordSystem.EPSG5174, "epsg5174")
    }

    @Test
    fun `SafetySpotType cdnType`() {
        roundTrip(SafetySpotType.CCTV, "cctv")
        roundTrip(SafetySpotType.STREET_LAMP, "lamp")
        roundTrip(SafetySpotType.BELL, "emergency_bell")
        roundTrip(SafetySpotType.SAFEHOUSE, "safehouse")
        roundTrip(SafetySpotType.POLICE, "police")
    }

    @Test
    fun `SafetySpotRefreshStatus never replaced kept`() {
        roundTrip(SafetySpotRefreshStatus.NEVER, "never")
        roundTrip(SafetySpotRefreshStatus.REPLACED, "replaced")
        roundTrip(SafetySpotRefreshStatus.KEPT, "kept")
    }
}
