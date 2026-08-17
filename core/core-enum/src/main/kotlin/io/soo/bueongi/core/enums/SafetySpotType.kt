package io.soo.bueongi.core.enums

import com.fasterxml.jackson.annotation.JsonValue

/** [cdnType] 이 와이어 계약([JsonValue]) — 정확히 cctv|lamp|security_lamp|emergency_bell|safehouse|police|pedestrian_signal 을 내야 한다. */
enum class SafetySpotType(@get:JsonValue val cdnType: String) {
    CCTV("cctv"),
    /** 가로등 — 간선도로·차도를 비추는 도로 조명(도로과 관리). 원천은 재난안전데이터 DSSP-IF-00084. */
    STREET_LAMP("lamp"),

    /** 보안등 — 골목·이면도로를 비추는 방범 조명(안전 부서 관리). 원천은 data.go.kr 표준데이터. 조명이라는 점은 같아 안전 점수는 [STREET_LAMP] 와 함께 센다. */
    SECURITY_LAMP("security_lamp"),
    BELL("emergency_bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),

    /** 보행신호등(법정 명칭) — 조명이 아니라 교통시설이라 조명 종류와 분리한다(조명 밀도 점수에 섞이면 안 된다). 원천 데이터명 '보행등'은 조명을 뜻하는 말이라 쓰지 않는다. */
    PEDESTRIAN_SIGNAL("pedestrian_signal"),
}
