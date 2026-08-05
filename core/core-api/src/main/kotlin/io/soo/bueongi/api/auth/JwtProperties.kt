package io.soo.bueongi.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bueongi.jwt")
data class JwtProperties(
    val publicKey: String,
    val privateKey: String,
    val issuer: String = "bueongi",
) {
    init {
        require(publicKey.isNotBlank() && !publicKey.trim().startsWith("\${")) {
            "JWT_PUBLIC_KEY must be provided via the JWT_PUBLIC_KEY env var (got blank or unresolved placeholder)"
        }
        require(privateKey.isNotBlank() && !privateKey.trim().startsWith("\${")) {
            "JWT_PRIVATE_KEY must be provided via the JWT_PRIVATE_KEY env var (got blank or unresolved placeholder)"
        }
    }
}
