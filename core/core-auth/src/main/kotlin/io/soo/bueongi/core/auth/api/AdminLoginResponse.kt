package io.soo.bueongi.core.auth.api

import io.soo.bueongi.core.auth.api.AdminUserResponse
import io.soo.bueongi.core.auth.domain.AdminTokenBundle

data class AdminLoginResponse(
    val token: AdminTokenBundle,
    val user: AdminUserResponse,
)
