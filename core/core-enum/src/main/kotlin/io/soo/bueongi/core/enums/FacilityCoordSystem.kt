package io.soo.bueongi.core.enums

import io.soo.bueongi.support.util.Epsg5174
import io.soo.bueongi.support.util.Epsg5179
import io.soo.bueongi.support.util.Wgs84Point

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/** [wireName] 이 와이어 계약([JsonValue]) — coordSystem 이 정확히 wgs84|epsg5179|epsg5174 를 내야 한다. */
enum class FacilityCoordSystem(@get:JsonValue val wireName: String) {
    WGS84("wgs84") {
        /** 이미 위경도라 투영 변환이 없다. 좌표는 원본 열에서 그대로 읽는다. */
        override fun toWgs84(x: Double, y: Double): Wgs84Point? = null
    },
    EPSG5179("epsg5179") {
        override fun toWgs84(x: Double, y: Double): Wgs84Point? = Epsg5179.toWgs84(x, y)
    },
    EPSG5174("epsg5174") {
        override fun toWgs84(x: Double, y: Double): Wgs84Point? = Epsg5174.toWgs84(x, y)
    },
    ;

    /** 이 좌표계의 평면 좌표를 위경도로 옮긴다. 범위를 벗어나면 null. */
    abstract fun toWgs84(x: Double, y: Double): Wgs84Point?

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityCoordSystem = entries.first { it.wireName == value }
    }
}
