package io.soo.bueongi.api.auth

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
    