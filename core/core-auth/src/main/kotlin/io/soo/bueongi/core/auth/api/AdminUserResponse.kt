package io.soo.bueongi.core.auth.api

data class AdminUserResponse(
    val id: Long,
    val provider: String,
    val email: String,
    val roles: List<String>,
)
