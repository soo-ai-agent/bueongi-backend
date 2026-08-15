package io.soo.bueongi.core.auth

import io.soo.bueongi.core.auth.domain.model.AdminTokenKind
import io.soo.bueongi.core.auth.domain.error.ExpiredTokenException
import io.soo.bueongi.core.auth.domain.error.InvalidTokenException
import io.soo.bueongi.core.auth.domain.config.JwtProperties
import io.soo.bueongi.core.auth.domain.service.JwtTokenVerifier

import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

class JwtTokenVerifierTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun verifierFor(publicKey: RSAPublicKey): JwtTokenVerifier {
        val pem: String = Base64.getEncoder().encodeToString(publicKey.encoded)
        return JwtTokenVerifier(JwtProperties(publicKey = pem, privateKey = "test-only"))
    }

    private fun token(
        signingKey: PrivateKey = keyPair.private,
        subject: String = "42",
        roles: List<String>? = listOf("ROLE_ADMIN"),
        expiresAt: Date = Date(System.currentTimeMillis() + 60_000),
    ): String {
        val builder = Jwts.builder()
            .issuer("bueongi")
            .subject(subject)
            .claim("tokenKind", AdminTokenKind.ACCESS.name)
            .issuedAt(Date())
            .expiration(expiresAt)
        if (roles != null) {
            builder.claim("roles", roles)
        }
        return builder.claim("nickname", "관리자").signWith(signingKey, Jwts.SIG.RS256).compact()
    }

    @Test
    fun `유효한 ROLE_ADMIN 토큰을 검증해 userId 와 roles 를 추출한다`() {
        val verified: JwtTokenVerifier.VerifiedToken = verifierFor(keyPair.public as RSAPublicKey).verify(token())

        assertThat(verified.userId).isEqualTo(42L)
        assertThat(verified.roles).containsExactly("ROLE_ADMIN")
        assertThat(verified.nickname).isEqualTo("관리자")
    }

    @Test
    fun `roles 클레임이 없으면 빈 목록으로 추출한다`() {
        val verified: JwtTokenVerifier.VerifiedToken = verifierFor(keyPair.public as RSAPublicKey).verify(token(roles = null))

        assertThat(verified.roles).isEmpty()
    }

    @Test
    fun `만료된 토큰은 ExpiredTokenException 이다`() {
        val expired: String = token(expiresAt = Date(System.currentTimeMillis() - 1_000))

        assertThatThrownBy { verifierFor(keyPair.public as RSAPublicKey).verify(expired) }
            .isInstanceOf(ExpiredTokenException::class.java)
    }

    @Test
    fun `다른 키로 서명한 토큰은 InvalidTokenException 이다`() {
        val otherKey: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged: String = token(signingKey = otherKey.private)

        assertThatThrownBy { verifierFor(keyPair.public as RSAPublicKey).verify(forged) }
            .isInstanceOf(InvalidTokenException::class.java)
    }
}
