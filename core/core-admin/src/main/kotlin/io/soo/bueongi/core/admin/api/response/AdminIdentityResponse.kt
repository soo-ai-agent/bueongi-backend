package io.soo.bueongi.core.admin.api.response

import io.soo.bueongi.core.admin.api.auth.AdminAuthenticationException
import io.soo.bueongi.core.admin.domain.AdminIdentity
import org.springframework.security.core.Authentication

data class AdminIdentityResponse(
    val userId: Long,
    val roles: List<String>,
) {
    companion object {

        /** principal 은 JwtAuthenticationFilter 가 넣은 userId(Long) 다. 아니면 인증 배선이 깨진 것이라 401 로 돌린다. */
        fun from(authentication: Authentication): AdminIdentityResponse {
            val userId: Long = authentication.principal as? Long ?: throw AdminAuthenticationException()
            return AdminIdentityResponse(userId = userId, roles = authentication.authorities.map { it.authority })
        }

        fun from(identity: AdminIdentity): AdminIdentityResponse =
            AdminIdentityResponse(userId = identity.userId, roles = identity.roles)
    }
}
