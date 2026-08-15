package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.api.auth.AdminAuthenticationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

class AdminIdentityResponseTest {

    private fun authentication(principal: Any): Authentication =
        UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))

    @Test
    fun `principal 이 userId 면 그대로 옮긴다`() {
        val response: AdminIdentityResponse = AdminIdentityResponse.from(authentication(7L))

        assertThat(response.userId).isEqualTo(7L)
        assertThat(response.roles).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `principal 이 userId 가 아니면 401 로 이어지는 인증 예외다`() {
        assertThatThrownBy { AdminIdentityResponse.from(authentication("anonymous")) }
            .isInstanceOf(AdminAuthenticationException::class.java)
    }
}
