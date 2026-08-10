package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [cdnType] 이 와이어 계약([JsonValue]) — 정확히 cctv|lamp|emergency_bell|safehouse|police 를 내야 한다. */
enum class FacilityType(@get:JsonValue val cdnType: String) {
    CCTV("cctv"),
    LAMP("lamp"),
    BELL("emergency_bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityType = entries.first { it.cdnType == value }
    }
}
