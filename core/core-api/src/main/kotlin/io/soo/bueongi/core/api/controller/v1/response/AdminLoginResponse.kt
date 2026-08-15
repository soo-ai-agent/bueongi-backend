package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.api.auth.AdminTokenBundle

data class AdminLoginResponse(
    val token: AdminTokenBundle,
    val user: AdminUserResponse,
)
