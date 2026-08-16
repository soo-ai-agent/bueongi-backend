package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.model.MAX_EXPIRES_HOURS
import io.soo.bueongi.core.share.domain.model.MIN_EXPIRES_HOURS

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/** 선택 항목은 클라이언트가 보내지 않을 수 있다. */
data class CreateShareRequest(
    @field:Min(MIN_EXPIRES_HOURS.toLong())
    @field:Max(MAX_EXPIRES_HOURS.toLong())
    @JsonProperty("expires_in_hours")
    val expiresInHours: Int? = null,
    @field:Min(1)
    @JsonProperty("eta_seconds")
    val etaSeconds: Int? = null,
)
