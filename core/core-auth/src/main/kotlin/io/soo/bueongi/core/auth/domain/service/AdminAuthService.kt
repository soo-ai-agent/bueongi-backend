package io.soo.bueongi.core.auth.domain.service

import io.soo.bueongi.core.auth.domain.config.AdminAuthProperties
import io.soo.bueongi.core.auth.domain.error.AdminAuthenticationException
import io.soo.bueongi.core.auth.api.AdminLoginRequest
import io.soo.bueongi.core.auth.api.AdminLoginResponse
import io.soo.bueongi.core.auth.api.AdminRefreshRequest
import io.soo.bueongi.core.auth.api.AdminRefreshResponse
import io.soo.bueongi.core.auth.api.AdminUserResponse

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
