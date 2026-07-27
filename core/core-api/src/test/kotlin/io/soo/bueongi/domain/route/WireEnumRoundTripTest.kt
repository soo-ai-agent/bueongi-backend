package io.soo.bueongi.domain.route

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.domain.admin.FacilityRefreshStatus
import io.soo.bueongi.domain.facility.FacilityCoordSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 와이어 판별자 enum 왕복 계약 — String → enum 전환이 JSON 을 바꾸지 않음을 못 박는다.
 *
 * 각 enum 값을 직렬화하면 정확히 기존 소문자 문자열을 내고(@JsonValue), 그 문자열을 역직렬화하면
 * 같은 값으로 돌아온다(@JsonCreator). 프로덕션/Redis 와 동일한 kotlin 모듈 매퍼로 검증한다.
 */
class WireEnumRoundTripTest {

    private val mapper = jacksonObjectMapper()

    private inline fun <reified T : Enum<T>> roundTrip(value: T, wire: String) {
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
    fun `FacilityPoiType 전 종류`() {
        roundTrip(FacilityPoiType.START, "start")
        roundTrip(FacilityPoiType.END, "end")
        roundTrip(FacilityPoiType.CCTV, "cctv")
        roundTrip(FacilityPoiType.BELL, "bell")
        roundTrip(FacilityPoiType.STORE, "store")
        roundTrip(FacilityPoiType.POLICE, "police")
        roundTrip(FacilityPoiType.SAFEHOUSE, "safehouse")
        roundTrip(FacilityPoiType.LAMP, "lamp")
    }

    @Test
    fun `SafetyPreference balanced safe safest`() {
        roundTrip(SafetyPreference.BALANCED, "balanced")
        roundTrip(SafetyPreference.SAFE, "safe")
        roundTrip(SafetyPreference.SAFEST, "safest")
    }

    @Test
    fun `FacilityCoordSystem wgs84 epsg5179 epsg5174`() {
        roundTrip(FacilityCoordSystem.WGS84, "wgs84")
        roundTrip(FacilityCoordSystem.EPSG5179, "epsg5179")
        roundTrip(FacilityCoordSystem.EPSG5174, "epsg5174")
    }

    @Test
    fun `FacilityType cdnType`() {
        roundTrip(FacilityType.CCTV, "cctv")
        roundTrip(FacilityType.LAMP, "lamp")
        roundTrip(FacilityType.BELL, "emergency_bell")
        roundTrip(FacilityType.SAFEHOUSE, "safehouse")
        roundTrip(FacilityType.POLICE, "police")
    }

    @Test
    fun `FacilityRefreshStatus never replaced kept`() {
        roundTrip(FacilityRefreshStatus.NEVER, "never")
        roundTrip(FacilityRefreshStatus.REPLACED, "replaced")
        roundTrip(FacilityRefreshStatus.KEPT, "kept")
    }
}
