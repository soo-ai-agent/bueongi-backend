package io.soo.bueongi.core.api.auth

import io.soo.bueongi.core.api.controller.v1.request.AdminLoginRequest
import io.soo.bueongi.core.api.controller.v1.response.AdminLoginResponse
import io.soo.bueongi.core.api.controller.v1.request.AdminRefreshRequest
import io.soo.bueongi.core.api.controller.v1.response.AdminRefreshResponse
import io.soo.bueongi.core.api.controller.v1.response.AdminUserResponse

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminAuthService(
    private val properties: AdminAuthProperties,
    private val tokenService: AdminTokenService,
) {
    private val passwordEncoder: BCryptPasswordEncoder = BCryptPasswordEncoder()

    fun login(request: AdminLoginRequest): AdminLoginResponse {
        if (request.loginId != properties.loginId || !passwordEncoder.matches(request.password, properties.passwordHash)) {
            throw AdminAuthenticationException()
        }
        return AdminLoginResponse(
            token = tokenService.issue(),
            user = AdminUserResponse(
                id = AdminTokenService.ADMIN_ID,
                provider = "BUEONGI",
                email = properties.email,
                roles = AdminTokenService.ADMIN_ROLES,
            ),
        )
    }

    fun refresh(request: AdminRefreshRequest): AdminRefreshResponse =
        AdminRefreshResponse(tokenService.refresh(request.refreshToken))
}
