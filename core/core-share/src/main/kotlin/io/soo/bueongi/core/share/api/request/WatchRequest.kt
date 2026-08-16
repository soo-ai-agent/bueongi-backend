package io.soo.bueongi.core.share.api.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class WatchRequest(
    @field:NotBlank
    @param:JsonProperty("owner_secret")
    // 보호자는 비밀키가 없어 null 이 가능함
    val ownerSecret: String? = null,
)
