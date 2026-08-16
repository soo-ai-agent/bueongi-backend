package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

/** 선택 항목은 클라이언트가 보내지 않을 수 있다. */
data class WatchRequest(
    @field:NotBlank
    @param:JsonProperty("owner_secret")
    val ownerSecret: String? = null,
)
