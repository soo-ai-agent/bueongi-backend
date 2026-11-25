package io.soo.bueongi.core.admin.auth.domain.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.soo.bueongi.core.admin.auth.domain.config.JwtProperties
import io.soo.bueongi.core.admin.auth.domain.config.parseJwtPublicKey
import io.soo.bueongi.core.admin.auth.domain.error.ExpiredTokenException
import io.soo.bueongi.core.admin.auth.domain.error.InvalidTokenException
import io.soo.bueongi.core.admin.auth.domain.model.AdminTokenKind
import io.soo.bueongi.core.admin.auth.domain.model.VerifiedToken
import java.security.interfaces.RSAPublicKey

class JwtTokenVerifier(
    key: RSAPublicKey,
    issuer: String,
) {
    private val tokenParser: JwtParser =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()

    constructor(properties: JwtProperties) : this(
        parseJwtPublicKey(properties.publicKey),
        properties.issuer,
    )

    fun verify(token: String): VerifiedToken {
        if (token.isBlank()) {
            throw InvalidTokenException("empty token")
        }
        val claims: Claims =
            try {
                tokenParser.parseSignedClaims(token).payload
            } catch (e: ExpiredJwtException) {
                throw ExpiredTokenException(e)
            } catch (e: JwtException) {
                throw InvalidTokenException("token verification failed", e)
            } catch (e: IllegalArgumentException) {
                throw InvalidTokenException("malformed token", e)
            }

        val userId: Long =
            claims.subject?.toLongOrNull()
                ?: throw InvalidTokenException("missing or non-numeric sub claim")

        val roles: List<String> =
            (claims["roles"] as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()

        if (claims["tokenKind"] != AdminTokenKind.ACCESS.name) {
            throw InvalidTokenException("access token required")
        }

        return VerifiedToken(
            userId = userId,
            roles = roles,
        )
    }
}
