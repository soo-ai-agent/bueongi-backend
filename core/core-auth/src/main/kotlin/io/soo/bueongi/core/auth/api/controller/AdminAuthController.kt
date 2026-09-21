package io.soo.bueongi.core.auth.api.controller

import io.soo.bueongi.core.auth.api.request.AdminLoginRequest
import io.soo.bueongi.core.auth.api.request.AdminRefreshRequest
import io.soo.bueongi.core.auth.api.response.AdminLoginResponse
import io.soo.bueongi.core.auth.api.response.AdminRefreshResponse
import io.soo.bueongi.core.auth.domain.service.AdminAuthService
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/auth")
class AdminAuthController(
    private val authService: AdminAuthService,
) {
    @PostMapping("/admin/login")
    fun login(
        @Valid @RequestBody request: AdminLoginRequest,
    ): AdminLoginResponse = authService.login(request)

    @PostMapping("/local/token/refresh")
    fun refresh(
        @Valid @RequestBody request: AdminRefreshRequest,
    ): AdminRefreshResponse = authService.refresh(request)
}
