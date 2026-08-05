package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 안심시설 요약. 프론트 SafetyFacilitySummary 와 동일 필드. pois 를 실제로 집계한 값이라
 * 화면에 렌더된 POI 와 항상 일치한다(없는 데이터를 지어내지 않음).
 */
data class FacilitySummary(
    val cctv: Int,
    val bell: Int,
    val store: Int,
    val police: Int,
    /** 여성안심지킴이집(B-2) 수. 지정 거점 — 영업시간 보장이 아님. */
    val safehouse: Int,
    /** 경로 회랑 안 가로등 수. */
    val lamp: Int,
    /** 안심시설 합계(start/end 제외). */
    val total: Int,
)
