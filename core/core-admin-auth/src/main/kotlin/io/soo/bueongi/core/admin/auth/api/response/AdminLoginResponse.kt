package io.soo.bueongi.core.admin.auth.api.response

import io.soo.bueongi.core.admin.auth.domain.model.AdminTokenBundle

data class AdminLoginResponse(
    val token: AdminTokenBundle,
    val user: AdminUserResponse,
) {
    companion object {
        fun from(token: AdminTokenBundle, email: String): AdminLoginResponse {
            return AdminLoginResponse(token = token, user = AdminUserResponse.from(email))
        }
    }
}
