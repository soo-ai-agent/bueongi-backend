package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// cdnType 이 와이어 계약(JsonValue) — cctv|lamp|security_lamp|emergency_bell|safehouse|police|pedestrian_signal|crime_prone_area.
// markerWire 는 지도에 나가는 응답(경로 마커·주변 집계)의 type 문자열 — core-route SafetySpotPoiType·모바일 MapMarkerType 과 글자 그대로 맞춘다.
enum class SafetySpotType(
    @get:JsonValue val cdnType: String,
    val label: String,
    // 지도 마커로 내보내지 않는 종류는 null 이 가능함.
    val markerWire: String?,
) {
    CCTV("cctv", "CCTV", "cctv"),

    // 원천: 재난안전데이터 DSSP-IF-00084.
    STREET_LAMP("lamp", "가로등", "lamp"),

    // 원천: data.go.kr 표준데이터.
    SECURITY_LAMP("security_lamp", "보안등", "security_lamp"),
    BELL("emergency_bell", "비상벨", "bell"),
    SAFEHOUSE("safehouse", "아동안전지킴이집", "safehouse"),
    POLICE("police", "치안시설", "police"),
    PEDESTRIAN_SIGNAL("pedestrian_signal", "보행신호등", "pedestrian_signal"),

    // 안심시설이 아니라 위험 신호 — 점수 차감에만 쓰고 지도 마커로는 내보내지 않는다.
    CRIME_PRONE_AREA("crime_prone_area", "범죄주의구간", null),
    ;

    companion object {
        fun ofCdnType(cdnType: String): SafetySpotType? {
            for (type in entries) {
                if (type.cdnType == cdnType) {
                    return type
                }
            }
            return null
        }
    }
}
