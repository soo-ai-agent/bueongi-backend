package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

// cdnType 이 와이어 계약(JsonValue) — cctv|lamp|security_lamp|emergency_bell|safehouse|police|pedestrian_signal|crime_prone_area.
enum class SafetySpotType(
    @get:JsonValue val cdnType: String,
) {
    CCTV("cctv"),

    // 원천: 재난안전데이터 DSSP-IF-00084.
    STREET_LAMP("lamp"),

    // 원천: data.go.kr 표준데이터.
    SECURITY_LAMP("security_lamp"),
    BELL("emergency_bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),
    PEDESTRIAN_SIGNAL("pedestrian_signal"),

    // 안심시설이 아니라 위험 신호 — 점수 차감에만 쓰고 지도 마커로는 내보내지 않는다.
    CRIME_PRONE_AREA("crime_prone_area"),
}
