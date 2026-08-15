package io.soo.bueongi.core.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [wire] 가 와이어 계약([JsonValue]) — 프론트 MapMock POI type 문자열을 정확히 내야 한다. */
enum class FacilityPoiType(@get:JsonValue val wire: String) {
    START("start"),
    END("end"),
    CCTV("cctv"),
    BELL("bell"),
    STORE("store"),
    POLICE("police"),
    SAFEHOUSE("safehouse"),
    LAMP("lamp"),
    PEDESTRIAN_SIGNAL("pedestrian_signal"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityPoiType = entries.first { it.wire == value }
    }
}
