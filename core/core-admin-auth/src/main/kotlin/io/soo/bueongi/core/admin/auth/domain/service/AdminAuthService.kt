package io.soo.bueongi.core.admin.auth.domain.service

import io.soo.bueongi.core.admin.auth.api.request.AdminLoginRequest
import io.soo.bueongi.core.admin.auth.api.request.AdminRefreshRequest
import io.soo.bueongi.core.admin.auth.api.response.AdminLoginResponse
import io.soo.bueongi.core.admin.auth.api.response.AdminRefreshResponse
import io.soo.bueongi.core.admin.auth.domain.config.AdminAuthProperties
import io.soo.bueongi.core.admin.auth.domain.error.AdminAuthenticationException
import io.soo.bueongi.core.admin.auth.domain.model.AdminTokenBundle
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminAuthService(
    private val properties: AdminAuthProperties,
    private val tokenService: AdminTokenService,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun login(request: AdminLoginRequest): AdminLoginResponse {
        val loginIdMatches: Boolean = request.loginId == properties.loginId
        val passwordMatches: Boolean = passwordEncoder.matches(request.password, properties.passwordHash)
        if (!loginIdMatches || !passwordMatches) {
            throw AdminAuthenticationException()
        }
        val token: AdminTokenBundle = tokenService.issue()
        return AdminLoginResponse.from(token, properties.email)
    }

    fun refresh(request: AdminRefreshRequest): AdminRefreshResponse {
        val reissued: AdminTokenBundle = tokenService.refresh(request.refreshToken)
        return AdminRefreshResponse.from(reissued)
    }
}
