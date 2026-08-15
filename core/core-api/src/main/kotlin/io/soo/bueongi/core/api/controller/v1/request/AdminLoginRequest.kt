package io.soo.bueongi.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class AdminLoginRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val password: String,
)
