package io.soo.bueongi.core.auth.api.response

import io.soo.bueongi.core.auth.domain.error.AdminAuthenticationException
import org.springframework.security.core.Authentication

data class AdminIdentityResponse(
    val userId: Long,
    val roles: List<String>,
) {
    companion object {

        fun from(authentication: Authentication): AdminIdentityResponse {
            val principal: Any? = authentication.principal
            if (principal !is Long) {
                throw AdminAuthenticationException()
            }
            val userId: Long = principal
            return AdminIdentityResponse(userId = userId, roles = authentication.authorities.map { it.authority })
        }

    }
}
