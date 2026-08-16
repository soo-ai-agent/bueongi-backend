package io.soo.bueongi.core.auth.api.response

import io.soo.bueongi.core.auth.domain.model.AdminTokenBundle

data class AdminLoginResponse(
    val token: AdminTokenBundle,
    val user: AdminUserResponse,
)
