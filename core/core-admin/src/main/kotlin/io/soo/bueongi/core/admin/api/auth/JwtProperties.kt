package io.soo.bueongi.core.admin.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bueongi.jwt")
data class JwtProperties(
    val publicKey: String,
    val privateKey: String,
    val issuer: String = "bueongi",
)
