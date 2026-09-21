package io.soo.bueongi.core.route.domain.marker.model

import com.fasterxml.jackson.annotation.JsonValue

/** [wire] 가 와이어 계약([JsonValue]) — 프론트 MapMock POI type 문자열을 정확히 내야 한다. */
enum class SafetySpotPoiType(@get:JsonValue val wire: String) {
    START("start"),
    END("end"),
    CCTV("cctv"),
    BELL("bell"),
    STORE("store"),
    POLICE("police"),
    SAFEHOUSE("safehouse"),
    STREET_LAMP("lamp"),
    SECURITY_LAMP("security_lamp"),
    PEDESTRIAN_SIGNAL("pedestrian_signal"),
}
