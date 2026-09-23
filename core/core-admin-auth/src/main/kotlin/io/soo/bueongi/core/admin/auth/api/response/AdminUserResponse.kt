package io.soo.bueongi.core.admin.auth.api.response

data class AdminUserResponse(
    val id: Long,
    val provider: String,
    val email: String,
    val roles: List<String>,
)
