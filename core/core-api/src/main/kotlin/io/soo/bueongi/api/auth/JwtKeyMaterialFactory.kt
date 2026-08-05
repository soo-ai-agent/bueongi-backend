package io.soo.bueongi.api.auth

import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

object JwtKeyMaterialFactory {
    fun create(properties: JwtProperties, localProfile: Boolean): JwtKeyMaterial {
        if (properties.publicKey.isNotBlank() && properties.privateKey.isNotBlank()) {
            return JwtKeyMaterial(
                publicKey = JwtTokenVerifier.parsePemPublicKey(properties.publicKey),
                privateKey = JwtPrivateKeyParser.parse(properties.privateKey),
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
}
