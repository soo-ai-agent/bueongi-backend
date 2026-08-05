package io.soo.bueongi.api.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** access token 형식/서명/클레임이 무효 → 401. */
class InvalidTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
