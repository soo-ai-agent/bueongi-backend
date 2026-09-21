package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class EndShareRequest(
    @field:NotBlank
    @param:JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)
