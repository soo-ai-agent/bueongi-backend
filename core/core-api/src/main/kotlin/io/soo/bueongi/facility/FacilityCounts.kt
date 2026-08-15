package io.soo.bueongi.facility

/** 경로 회랑 안에서 센 시설 수. 안전 점수 계산의 입력이다. */
data class FacilityCounts(
    val cctv: Int,
    val lamp: Int,
    val bell: Int,
    val safehouse: Int,
    val police: Int,
)
