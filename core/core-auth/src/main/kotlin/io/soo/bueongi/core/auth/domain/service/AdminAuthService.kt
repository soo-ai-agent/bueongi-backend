package io.soo.bueongi.core.auth.domain.service

import io.soo.bueongi.core.auth.domain.config.AdminAuthProperties
import io.soo.bueongi.core.auth.domain.error.AdminAuthenticationException
import io.soo.bueongi.core.auth.api.request.AdminLoginRequest
import io.soo.bueongi.core.auth.api.response.AdminLoginResponse
import io.soo.bueongi.core.auth.api.request.AdminRefreshRequest
import io.soo.bueongi.core.auth.api.response.AdminRefreshResponse
import io.soo.bueongi.core.auth.api.response.AdminUserResponse

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminAuthService(
    private val properties: AdminAuthProperties,
    private val tokenService: AdminTokenService,
) {
    private val passwordEncoder: BCryptPasswordEncoder = BCryptPasswordEncoder()

    fun login(request: AdminLoginRequest): AdminLoginResponse {
        val loginIdMatches: Boolean = request.loginId == properties.loginId
        // 아이디가 틀려도 비밀번호 대조를 건너뛰지 않는다 — 응답 시간 차이로 아이디를 알아낼 수 있다.
        val passwordMatches: Boolean = passwordEncoder.matches(request.password, properties.passwordHash)
        if (!loginIdMatches || !passwordMatches) {
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
