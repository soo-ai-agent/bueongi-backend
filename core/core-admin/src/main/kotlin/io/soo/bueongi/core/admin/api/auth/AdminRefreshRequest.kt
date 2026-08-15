package io.soo.bueongi.core.admin.api.auth

import jakarta.validation.constraints.NotBlank

data class AdminRefreshRequest(
    @field:NotBlank val refreshToken: String,
)
