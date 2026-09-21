package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// wireName 이 와이어 계약(JsonValue) — coordSystem 이 정확히 wgs84|epsg5179|epsg5174 를 내야 한다.
enum class SafetySpotCoordSystem(
    @get:JsonValue val wireName: String,
) {
    WGS84("wgs84"),
    EPSG5179("epsg5179"),
    EPSG5174("epsg5174"),
}
