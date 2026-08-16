package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.MAX_EXPIRES_HOURS
import io.soo.bueongi.core.share.domain.model.MIN_EXPIRES_HOURS

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class CreateShareRequest(
    @field:Min(MIN_EXPIRES_HOURS.toLong())
    @field:Max(MAX_EXPIRES_HOURS.toLong())
    @param:JsonProperty("expires_in_hours")
    // 안 보내면 null 이 가능함 — 기본 만료를 쓴다
    val expiresInHours: Int? = null,
    @field:Min(1)
    @param:JsonProperty("eta_seconds")
    // 도착 예정을 넣지 않으면 null 이 가능함
    val etaSeconds: Int? = null,
)
