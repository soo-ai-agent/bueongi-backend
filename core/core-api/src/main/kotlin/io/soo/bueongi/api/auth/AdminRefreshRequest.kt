package io.soo.bueongi.api.auth

import jakarta.validation.constraints.NotBlank

data class AdminRefreshRequest(
    @field:NotBlank val refreshToken: String,
)
