package io.soo.bueongi.core.share.api.request

import io.soo.bueongi.core.share.domain.MAX_EXPIRES_HOURS
import io.soo.bueongi.core.share.domain.MIN_EXPIRES_HOURS

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class CreateShareRequest(
    @field:Min(MIN_EXPIRES_HOURS.toLong())
    @field:Max(MAX_EXPIRES_HOURS.toLong())
    @JsonProperty("expires_in_hours")
    val expiresInHours: Int? = null,
    @field:Min(1)
    @JsonProperty("eta_seconds")
    val etaSeconds: Int? = null,
)
