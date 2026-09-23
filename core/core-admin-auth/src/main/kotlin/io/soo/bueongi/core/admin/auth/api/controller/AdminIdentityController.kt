package io.soo.bueongi.core.admin.auth.api.controller

import io.soo.bueongi.core.admin.auth.api.response.AdminIdentityResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/admin")
class AdminIdentityController {
    @GetMapping("/me")
    fun me(authentication: Authentication): AdminIdentityResponse {
        return AdminIdentityResponse.from(authentication)
    }
}
