package io.soo.bueongi.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bueongi.admin-auth")
data class AdminAuthProperties(
    val loginId: String,
    val passwordHash: String,
    val email: String = "admin@bueongi.local",
    val accessTokenSeconds: Long = 900,
    val refreshTokenSeconds: Long = 604800,
) {
    init {
        require(loginId.isNotBlank()) { "BUEONGI_ADMIN_LOGIN_ID must not be blank" }
        require(passwordHash.startsWith("\$2")) { "BUEONGI_ADMIN_PASSWORD_HASH must be a bcrypt hash" }
        require(accessTokenSeconds > 0) { "BUEONGI_ADMIN_ACCESS_TOKEN_SECONDS must be positive" }
        require(refreshTokenSeconds > accessTokenSeconds) {
            "BUEONGI_ADMIN_REFRESH_TOKEN_SECONDS must be greater than access token lifetime"
        }
    }
}
