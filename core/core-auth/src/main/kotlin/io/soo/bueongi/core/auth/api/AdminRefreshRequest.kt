package io.soo.bueongi.core.auth.api

import jakarta.validation.constraints.NotBlank

data class AdminRefreshRequest(
    @field:NotBlank val refreshToken: String,
)
