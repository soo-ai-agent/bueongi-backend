package io.soo.bueongi.core.admin.auth.api.request

import jakarta.validation.constraints.NotBlank

data class AdminRefreshRequest(
    @field:NotBlank val refreshToken: String,
)
