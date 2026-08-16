package io.soo.bueongi.core.auth.domain.service

import io.soo.bueongi.core.auth.domain.config.JwtProperties
import io.soo.bueongi.core.auth.domain.model.JwtKeyMaterial
import io.soo.bueongi.support.util.parseJwtPrivateKey
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

fun createJwtKeyMaterial(properties: JwtProperties, localProfile: Boolean): JwtKeyMaterial {
    if (properties.publicKey.isNotBlank() && properties.privateKey.isNotBlank()) {
        return JwtKeyMaterial(
            publicKey = JwtTokenVerifier.parsePemPublicKey(properties.publicKey),
            privateKey = parseJwtPrivateKey(properties.privateKey),
            issuer = properties.issuer,
        )
    }
    require(localProfile) { "JWT_PUBLIC_KEY and JWT_PRIVATE_KEY must be provided" }
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    return JwtKeyMaterial(
        publicKey = pair.public as RSAPublicKey,
        privateKey = pair.private as RSAPrivateKey,
        issuer = properties.issuer,
    )
}
