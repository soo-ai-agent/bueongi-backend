package io.soo.bueongi.core.admin.auth.domain.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.soo.bueongi.core.admin.auth.domain.config.AdminAuthProperties
import io.soo.bueongi.core.admin.auth.domain.error.AdminAuthenticationException
import io.soo.bueongi.core.admin.auth.domain.model.AdminTokenBundle
import io.soo.bueongi.core.admin.auth.domain.model.AdminTokenKind
import io.soo.bueongi.core.admin.auth.domain.model.JwtKeyMaterial
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

class AdminTokenService(
    keyMaterial: JwtKeyMaterial,
    private val authProperties: AdminAuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val privateKey: RSAPrivateKey = keyMaterial.privateKey
    private val publicKey: RSAPublicKey = keyMaterial.publicKey
    private val issuer: String = keyMaterial.issuer

    private val tokenParser: JwtParser =
        Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(issuer)
            .build()

    fun issue(): AdminTokenBundle =
        AdminTokenBundle(
            accessToken = create(AdminTokenKind.ACCESS, authProperties.accessTokenSeconds),
            tokenType = "Bearer",
            expiresIn = authProperties.accessTokenSeconds,
            refreshToken = create(AdminTokenKind.REFRESH, authProperties.refreshTokenSeconds),
            refreshExpiresIn = authProperties.refreshTokenSeconds,
        )

    fun refresh(refreshToken: String): AdminTokenBundle {
        val claims: Claims =
            try {
                tokenParser.parseSignedClaims(refreshToken).payload
            } catch (_: JwtException) {
                throw AdminAuthenticationException()
            } catch (_: IllegalArgumentException) {
                throw AdminAuthenticationException()
            }
        val issuedToAdmin: Boolean = claims.subject == ADMIN_ID.toString()
        val isRefreshToken: Boolean = claims[TOKEN_KIND_CLAIM] == AdminTokenKind.REFRESH.name
        if (!issuedToAdmin || !isRefreshToken) {
            throw AdminAuthenticationException()
        }
        // 재발급 토큰은 다시 만들지 않는다 — 다시 만들면 만료가 계속 밀려, 새어 나간 토큰 하나로 끝없이 재발급할 수 있다.
        val refreshExpiresAt: Instant = claims.expiration.toInstant()
        return AdminTokenBundle(
            accessToken = create(AdminTokenKind.ACCESS, authProperties.accessTokenSeconds),
            tokenType = "Bearer",
            expiresIn = authProperties.accessTokenSeconds,
            refreshToken = refreshToken,
            refreshExpiresIn = Duration.between(clock.instant(), refreshExpiresAt).seconds,
        )
    }

    private fun create(kind: AdminTokenKind, lifetimeSeconds: Long): String {
        val issuedAt: Instant = clock.instant()
        return Jwts.builder()
            .issuer(issuer)
            .subject(ADMIN_ID.toString())
            .id(UUID.randomUUID().toString())
            .claim("nickname", "관리자")
            .claim(TOKEN_KIND_CLAIM, kind.name)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(lifetimeSeconds)))
            .apply {
                if (kind == AdminTokenKind.ACCESS) {
                    claim("roles", ADMIN_ROLES)
                }
            }
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }

    companion object {
        const val ADMIN_ID: Long = 1L
        val ADMIN_ROLES: List<String> = listOf("ROLE_ADMIN")
        private const val TOKEN_KIND_CLAIM: String = "tokenKind"
    }
}
