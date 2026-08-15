package io.soo.bueongi.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class AdminRefreshRequest(
    @field:NotBlank val refreshToken: String,
)
