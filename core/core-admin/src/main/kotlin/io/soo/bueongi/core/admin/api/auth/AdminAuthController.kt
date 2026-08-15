package io.soo.bueongi.core.admin.api.auth

import io.soo.bueongi.core.admin.api.auth.AdminAuthService
import io.soo.bueongi.core.admin.api.auth.AdminLoginRequest
import io.soo.bueongi.core.admin.api.auth.AdminLoginResponse
import io.soo.bueongi.core.admin.api.auth.AdminRefreshRequest
import io.soo.bueongi.core.admin.api.auth.AdminRefreshResponse

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
    fun login(@Valid @RequestBody request: AdminLoginRequest):
            AdminLoginResponse = authService.login(request)

    @PostMapping("/local/token/refresh")
    fun refresh(@Valid @RequestBody request: AdminRefreshRequest):
            AdminRefreshResponse = authService.refresh(request)
}
