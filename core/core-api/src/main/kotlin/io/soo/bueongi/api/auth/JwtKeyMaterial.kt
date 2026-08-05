package io.soo.bueongi.api.auth

import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

data class JwtKeyMaterial(
    val publicKey: RSAPublicKey,
    val privateKey: RSAPrivateKey,
    val issuer: String,
)
