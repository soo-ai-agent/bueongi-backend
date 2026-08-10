package io.soo.bueongi.domain.route

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class FacilitySummary(
    val cctv: Int,
    val bell: Int,
    val store: Int,
    val police: Int,
    val safehouse: Int,
    val lamp: Int,
    val total: Int,
)
