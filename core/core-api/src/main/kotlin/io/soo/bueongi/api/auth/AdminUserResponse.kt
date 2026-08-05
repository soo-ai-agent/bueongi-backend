package io.soo.bueongi.api.auth

data class AdminUserResponse(
    val id: Long,
    val provider: String,
    val email: String,
    val roles: List<String>,
)
