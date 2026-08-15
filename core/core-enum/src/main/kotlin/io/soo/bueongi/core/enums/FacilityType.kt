package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [cdnType] 이 와이어 계약([JsonValue]) — 정확히 cctv|lamp|emergency_bell|safehouse|police|pedestrian_signal 을 내야 한다. */
enum class FacilityType(@get:JsonValue val cdnType: String) {
    CCTV("cctv"),
    LAMP("lamp"),
    BELL("emergency_bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),

    /** 보행자 신호등 — 조명이 아니라 교통시설이라 LAMP 와 분리한다(조명 밀도 점수에 섞이면 안 된다). */
    PEDESTRIAN_SIGNAL("pedestrian_signal"),
    ;

    companion object {

        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityType = entries.first { it.cdnType == value }
    }
}
