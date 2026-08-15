package io.soo.bueongi.core.api.auth

data class AdminTokenBundle(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
)
