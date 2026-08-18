package io.soo.bueongi.core.route.domain.safety.model

/** 경로 회랑 안에서 센 지점 수. 안전 점수 계산의 입력이다. */
data class SafetySpotCounts(
    val cctv: Int,
    val lamp: Int,
    val bell: Int,
    val safehouse: Int,
    val police: Int,
    /** 우범지역 및 공폐허가 — 유일하게 점수를 깎는 값이다. */
    val crimeProneArea: Int,
)
