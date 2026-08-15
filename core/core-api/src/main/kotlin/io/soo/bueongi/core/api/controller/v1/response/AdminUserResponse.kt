package io.soo.bueongi.core.api.controller.v1.response

data class AdminUserResponse(
    val id: Long,
    val provider: String,
    val email: String,
    val roles: List<String>,
)
