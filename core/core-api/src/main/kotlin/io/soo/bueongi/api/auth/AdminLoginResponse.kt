package io.soo.bueongi.api.auth

data class AdminLoginResponse(
    val token: AdminTokenBundle,
    val user: AdminUserResponse,
)
