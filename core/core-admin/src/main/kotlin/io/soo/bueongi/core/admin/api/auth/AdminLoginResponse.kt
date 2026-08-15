package io.soo.bueongi.core.admin.api.auth

import io.soo.bueongi.core.admin.api.response.AdminUserResponse
import io.soo.bueongi.core.admin.api.auth.AdminTokenBundle

data class AdminLoginResponse(
    val token: AdminTokenBundle,
    val user: AdminUserResponse,
)
