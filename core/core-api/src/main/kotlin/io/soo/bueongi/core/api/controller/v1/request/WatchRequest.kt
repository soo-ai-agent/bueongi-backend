package io.soo.bueongi.core.api.controller.v1.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class WatchRequest(
    @field:NotBlank
    @JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)
