package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class EndShareRequest(
    @field:NotBlank
    @param:JsonProperty("owner_secret")
    // 본문 없이 부르면 null 이 가능함
    val ownerSecret: String? = null,
)
