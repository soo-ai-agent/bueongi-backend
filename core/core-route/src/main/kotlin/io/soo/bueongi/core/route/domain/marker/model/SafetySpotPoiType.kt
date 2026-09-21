package io.soo.bueongi.core.route.domain.marker.model

import com.fasterxml.jackson.annotation.JsonValue

// wire 값은 프론트 POI type 문자열과 글자 그대로 맞춰야 하는 와이어 계약이다.
enum class SafetySpotPoiType(
    @get:JsonValue val wire: String,
) {
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
