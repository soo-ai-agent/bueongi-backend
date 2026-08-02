package io.soo.bueongi.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 관리자 인증용 JWT 검증 설정. [publicKey] 는 soo-auth-service 가 RSA 개인키로 서명한 access token 을
 * 검증하기 위한 대응 공개키다. 운영에서는 반드시 `JWT_PUBLIC_KEY` 환경변수로 주입한다.
 *
 * application.yml 은 `public-key: ${'$'}{JWT_PUBLIC_KEY:}` 로 선언하므로, 미주입 환경에서는 빈 값이
 * 바인딩되고 아래 [init] 가드가 컨텍스트 startup 을 fail-fast 시킨다(잘못된 무보호 기동 방지).
 */
@ConfigurationProperties(prefix = "bueongi.jwt")
data class JwtProperties(
    val publicKey: String,
    val issuer: String = "soo-auth",
) {
    init {
        require(publicKey.isNotBlank() && !publicKey.trim().startsWith("\${")) {
            "JWT_PUBLIC_KEY must be provided via the JWT_PUBLIC_KEY env var (got blank or unresolved placeholder)"
        }
    }
}
