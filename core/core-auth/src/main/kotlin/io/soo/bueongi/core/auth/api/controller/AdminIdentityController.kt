package io.soo.bueongi.core.auth.api.controller

import io.soo.bueongi.core.auth.api.response.AdminIdentityResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 지금 인증된 주체가 누구인지 답한다. 경로가 /api/admin 아래인 것은 프론트 계약이다. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/admin")
class AdminIdentityController {

    @GetMapping("/me")
    fun me(authentication: Authentication): AdminIdentityResponse = AdminIdentityResponse.from(authentication)
}
