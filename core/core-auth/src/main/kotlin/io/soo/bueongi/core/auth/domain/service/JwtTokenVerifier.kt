package io.soo.bueongi.core.auth.domain.service

import io.soo.bueongi.core.auth.domain.config.JwtProperties
import io.soo.bueongi.core.auth.domain.error.ExpiredTokenException
import io.soo.bueongi.core.auth.domain.error.InvalidTokenException
import io.soo.bueongi.core.enums.AdminTokenKind

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class JwtTokenVerifier(
    private val key: RSAPublicKey,
    private val issuer: String,
) {

    constructor(properties: JwtProperties) : this(
        parsePemPublicKey(properties.publicKey),
        properties.issuer,
    )

    data class VerifiedToken(
        val userId: Long,
        val roles: List<String>,
        val nickname: String?,
    )

    fun verify(token: String): VerifiedToken {
        if (token.isBlank()) {
            throw InvalidTokenException("empty token")
        }
        val claims: Claims = try {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw ExpiredTokenException(e)
        } catch (e: JwtException) {
            throw InvalidTokenException("token verification failed", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidTokenException("malformed token", e)
        }

        val userId: Long = claims.subject?.toLongOrNull()
            ?: throw InvalidTokenException("missing or non-numeric sub claim")

        val roles: List<String> = (claims["roles"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

        if (claims["tokenKind"] != AdminTokenKind.ACCESS.name) {
            throw InvalidTokenException("access token required")
        }

        return VerifiedToken(
            userId = userId,
            roles = roles,
            nickname = claims["nickname"] as? String,
        )
    }

    companion object {
        fun parsePemPublicKey(pem: String): RSAPublicKey {
            val stripped: String = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val decoded: ByteArray = Base64.getDecoder().decode(stripped)
            val keySpec: X509EncodedKeySpec = X509EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
        }
    }
}
