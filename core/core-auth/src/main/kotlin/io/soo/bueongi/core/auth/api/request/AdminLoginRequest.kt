package io.soo.bueongi.core.auth.api.request

import jakarta.validation.constraints.NotBlank

data class AdminLoginRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val password: String,
)
