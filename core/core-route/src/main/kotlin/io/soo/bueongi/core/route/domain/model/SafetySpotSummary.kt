package io.soo.bueongi.core.route.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SafetySpotSummary(
    val cctv: Int,
    val bell: Int,
    @param:JsonProperty("store")
    val convenienceStore: Int,
    val police: Int,
    val safehouse: Int,
    val lamp: Int,
    val securityLamp: Int,
    val pedestrianSignal: Int,
    val total: Int,
)
