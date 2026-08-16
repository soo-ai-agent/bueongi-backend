package io.soo.bueongi.core.auth

import io.soo.bueongi.core.auth.domain.config.AdminAuthProperties
import io.soo.bueongi.core.auth.domain.service.AdminAuthService
import io.soo.bueongi.core.auth.domain.error.AdminAuthenticationException
import io.soo.bueongi.core.auth.api.request.AdminLoginRequest
import io.soo.bueongi.core.auth.domain.service.AdminTokenService
import io.soo.bueongi.core.auth.domain.error.InvalidTokenException
import io.soo.bueongi.core.auth.domain.service.JwtKeyMaterialFactory
import io.soo.bueongi.core.auth.domain.config.JwtProperties
import io.soo.bueongi.core.auth.domain.service.JwtTokenVerifier

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64

class AdminAuthServiceTest {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val jwt = JwtProperties(
        publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
        privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded),
    )
    private val auth = AdminAuthProperties(
        loginId = "admin",
        passwordHash = BCryptPasswordEncoder().encode("password"),
    )
    private val keys = JwtKeyMaterialFactory.create(jwt, false)
    private val tokens = AdminTokenService(keys, auth)
    private val service = AdminAuthService(auth, tokens)

    @Test
    fun `관리자 로그인은 부엉이 토큰과 관리자 정보를 반환한다`() {
        val response = service.login(AdminLoginRequest("admin", "password"))
        val verified = JwtTokenVerifier(jwt).verify(response.token.accessToken)

        assertThat(response.user.provider).isEqualTo("BUEONGI")
        assertThat(response.user.roles).containsExactly("ROLE_ADMIN")
        assertThat(verified.userId).isEqualTo(AdminTokenService.ADMIN_ID)
        assertThat(verified.roles).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `잘못된 비밀번호는 인증에 실패한다`() {
        assertThatThrownBy { service.login(AdminLoginRequest("admin", "wrong")) }
            .isInstanceOf(AdminAuthenticationException::class.java)
    }

    @Test
    fun `잘못된 아이디는 비밀번호가 맞아도 인증에 실패한다`() {
        assertThatThrownBy { service.login(AdminLoginRequest("wrong", "password")) }
            .isInstanceOf(AdminAuthenticationException::class.java)
    }

    @Test
    fun `리프레시 토큰은 관리자 API access token으로 사용할 수 없다`() {
        val bundle = tokens.issue()

        assertThatThrownBy { JwtTokenVerifier(jwt).verify(bundle.refreshToken) }
            .isInstanceOf(InvalidTokenException::class.java)
        assertThat(tokens.refresh(bundle.refreshToken).accessToken).isNotBlank()
    }
}
