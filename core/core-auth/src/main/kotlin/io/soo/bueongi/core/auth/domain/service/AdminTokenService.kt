package io.soo.bueongi.core.auth.domain.service

import io.soo.bueongi.core.auth.domain.config.AdminAuthProperties
import io.soo.bueongi.core.auth.domain.error.AdminAuthenticationException
import io.soo.bueongi.core.auth.domain.model.AdminTokenBundle
import io.soo.bueongi.core.auth.domain.model.JwtKeyMaterial
import io.soo.bueongi.core.auth.domain.model.AdminTokenKind

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
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

    fun issue(): AdminTokenBundle =
        AdminTokenBundle(
        accessToken = createToken(AdminTokenKind.ACCESS, authProperties.accessTokenSeconds),
        tokenType = "Bearer",
        expiresIn = authProperties.accessTokenSeconds,
        refreshToken = createToken(AdminTokenKind.REFRESH, authProperties.refreshTokenSeconds),
        refreshExpiresIn = authProperties.refreshTokenSeconds,
    )

    fun refresh(refreshToken: String): AdminTokenBundle {
        val claims: Claims = try {
            Jwts.parser().verifyWith(publicKey).requireIssuer(issuer).build().parseSignedClaims(refreshToken).payload
        } catch (exception: JwtException) {
            throw AdminAuthenticationException()
        } catch (exception: IllegalArgumentException) {
            throw AdminAuthenticationException()
        }
        if (claims.subject != ADMIN_ID.toString() || claims[TOKEN_KIND_CLAIM] != AdminTokenKind.REFRESH.name) {
            throw AdminAuthenticationException()
        }
        return issue()
    }

    private fun createToken(kind: AdminTokenKind, lifetimeSeconds: Long): String {
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
