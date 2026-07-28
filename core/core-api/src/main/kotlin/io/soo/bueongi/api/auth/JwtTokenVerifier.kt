package io.soo.bueongi.api.auth

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * soo-auth-service 가 RS256 으로 발급한 access token 을 RSA 공개키로 오프라인 검증한다.
 *
 * Claim 규약(auth-service 기준):
 *  - `sub`   = userId (Long)
 *  - `roles` = 권한 목록(List<String>, 예: `["ROLE_ADMIN"]`)
 *  - `nickname` (선택)
 *  - `iat`, `exp`
 *
 * 검증 실패는 만료([ExpiredTokenException])와 그 외 무효([InvalidTokenException])로 구분해,
 * 필터가 사유에 맞는 401 응답으로 변환한다.
 *
 * [SecurityConfig] 의 @Bean 으로만 등록된다(servlet 웹 컨텍스트 한정). @Component 로 두면 컴포넌트 스캔이
 * 비웹/슬라이스 컨텍스트에서도 생성해 JwtProperties(공개키) 주입을 강제하므로 @Bean 으로 격리한다.
 */
class JwtTokenVerifier(props: JwtProperties) {

    private val key: RSAPublicKey = parsePemPublicKey(props.publicKey)

    data class VerifiedToken(
        val userId: Long,
        val roles: List<String>,
        val nickname: String?,
    )

    fun verify(token: String): VerifiedToken {
        if (token.isBlank()) {
            throw InvalidTokenException("empty token")
        }
        val claims = try {
            Jwts.parser()
                .verifyWith(key)
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

        return VerifiedToken(
            userId = userId,
            roles = roles,
            nickname = claims["nickname"] as? String,
        )
    }

    companion object {
        /**
         * PEM 또는 base64-DER 형식의 RSA 공개키 문자열을 [RSAPublicKey] 로 변환한다.
         * 헤더("-----BEGIN PUBLIC KEY-----" 등)와 공백은 자동 제거한다.
         */
        fun parsePemPublicKey(pem: String): RSAPublicKey {
            val stripped: String = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val decoded = Base64.getDecoder().decode(stripped)
            val keySpec: X509EncodedKeySpec = X509EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
        }
    }
}

/** access token 이 만료됨 → 401. */
class ExpiredTokenException(cause: Throwable? = null) : RuntimeException("access token expired", cause)

/** access token 형식/서명/클레임이 무효 → 401. */
class InvalidTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
