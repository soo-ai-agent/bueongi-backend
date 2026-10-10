package io.soo.bueongi.core.admin.auth.api.response

import io.soo.bueongi.core.admin.auth.domain.service.AdminTokenService

data class AdminUserResponse(
    val id: Long,
    val provider: String,
    val email: String,
    val roles: List<String>,
) {
    companion object {
        fun from(email: String): AdminUserResponse {
            return AdminUserResponse(
                id = AdminTokenService.ADMIN_ID,
                provider = "BUEONGI",
                email = email,
                roles = AdminTokenService.ADMIN_ROLES,
            )
        }
    }
}
